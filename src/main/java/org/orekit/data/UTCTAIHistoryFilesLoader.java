/* Copyright 2002-2008 CS Communication & Systèmes
 * Licensed to CS Communication & Systèmes (CS) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * CS licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.orekit.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.orekit.errors.OrekitException;
import org.orekit.time.ChunkedDate;
import org.orekit.time.UTCTAIOffset;


/** Loader for UTC versus TAI history files.
 * <p>UTC versus TAI history files contain {@link UTCTAIOffset
 * leap seconds} data since.</p>
 * <p>The UTC versus TAI history files are recognized thanks to their
 * base names, which must match the pattern <code>UTC-TAI.history</code>
 * (or <code>UTC-TAI.history.gz</code> for gzip-compressed files)</p>
 * <p>Only one history file must be present in the IERS directories
 * hierarchy.</p>
 * @author Luc Maisonobe
 * @version $Revision:1665 $ $Date:2008-06-11 12:12:59 +0200 (mer., 11 juin 2008) $
 */
public class UTCTAIHistoryFilesLoader extends DataFileCrawler {

    /** Regular data lines pattern. */
    private Pattern regularPattern;

    /** Last line pattern pattern. */
    private Pattern lastPattern;

    /** Months map. */
    private Map<String,Integer> monthsMap;

    /** Time scales offsets. */
    private SortedMap<ChunkedDate, Integer> entries;

    /** Build a loader for UTC-TAI history file. */
    public UTCTAIHistoryFilesLoader() {

        super("^UTC-TAI\\.history(?:\\.gz)?$");

        // the data lines in the UTC time steps data files have the following form:
        // 1966  Jan.  1 - 1968  Feb.  1     4.313 170 0s + (MJD - 39 126) x 0.002 592s
        // 1968  Feb.  1 - 1972  Jan.  1     4.213 170 0s +        ""
        // 1972  Jan.  1 -       Jul.  1    10s
        //       Jul.  1 - 1973  Jan.  1    11s
        // 1973  Jan.  1 - 1974  Jan.  1    12s
        //  ...
        // 2006  Jan.  1.-                  33s
        // we ignore the non-constant and non integer offsets before 1972-01-01
        final String start       = "^";
        final String yearField   = "\\p{Blank}*((?:\\p{Digit}\\p{Digit}\\p{Digit}\\p{Digit})|(?:    ))";
        final String monthField  = "\\p{Blank}+(\\p{Upper}\\p{Lower}+)\\.?";
        final String dayField    = "\\p{Blank}+([ 0-9]+)\\.?";
        final String offsetField = "\\p{Blank}+(\\p{Digit}+)s";
        final String separator   = "\\p{Blank}*-\\p{Blank}+";
        final String finalBlanks = "\\p{Blank}*$";
        regularPattern = Pattern.compile(start + yearField + monthField + dayField +
                                         separator + yearField + monthField + dayField +
                                         offsetField + finalBlanks);
        lastPattern    = Pattern.compile(start + yearField + monthField + dayField +
                                         separator + offsetField + finalBlanks);
        monthsMap = new HashMap<String, Integer>(12);
        final String[] months = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        };
        for (int i = 0; i < months.length; ++i) {
            monthsMap.put(months[i], i + 1);
        }

    }

    /** Load UTC-TAI offsets entries read from one or more files.
     * <p>The time steps are extracted from the <code>UTC-TAI.history[.gz]</code>
     * files. Since entries are stored in a {@link java.util.SortedMap SortedMap},
     * they are chronologically sorted and only one entry remains for a given date.
     * If different files contain inconsistent data for the same date, the selected
     * entry will depend on file analysis order, which is undefined.</p>
     * @exception OrekitException if some data can't be read or some
     * file content is corrupted
     */
    public SortedMap<ChunkedDate, Integer> loadTimeSteps() throws OrekitException {
        entries = new TreeMap<ChunkedDate, Integer>();
        new DataDirectoryCrawler().crawl(this);
        return entries;
    }

    /** {@inheritDoc} */
    protected void visit(final BufferedReader reader)
        throws OrekitException, IOException, ParseException {

        // read all file, ignoring not recognized lines
        boolean foundEntries = false;
        final String emptyYear = "    ";
        int lineNumber = 0;
        ChunkedDate lastDate = null;
        int lastLine = 0;
        String previousYear = emptyYear;
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            ++lineNumber;

            // check matching for regular lines and last line
            Matcher matcher = regularPattern.matcher(line);
            if (matcher.matches()) {
                if (lastLine > 0) {
                    throw new OrekitException("unexpected data after line {0} in file {1}: {2}",
                                              new Object[] {
                                                  Integer.valueOf(lastLine),
                                                  getFile().getAbsolutePath(),
                                                  line
                                              });
                }
            } else {
                matcher = lastPattern.matcher(line);
                if (matcher.matches()) {
                    // this is the last line (there is a start date but no end date)
                    lastLine = lineNumber;
                }
            }

            if (matcher.matches()) {
                try {
                    // build an entry from the extracted fields

                    String year = matcher.group(1);
                    if (emptyYear.equals(year)) {
                        year = previousYear;
                    }
                    if (lineNumber != lastLine) {
                        if (emptyYear.equals(matcher.group(4))) {
                            previousYear = year;
                        } else {
                            previousYear = matcher.group(4);
                        }
                    }
                    Integer month = monthsMap.get(matcher.group(2));
                    if (month == null) {
                        throw new NumberFormatException();
                    }
                    final ChunkedDate leapDay = new ChunkedDate(Integer.parseInt(year.trim()),
                                                                month.intValue(),
                                                                Integer.parseInt(matcher.group(3).trim()));

                    final Integer offset = Integer.valueOf(matcher.group(matcher.groupCount()));
                    if ((lastDate != null) && leapDay.compareTo(lastDate) <= 0) {
                        throw new OrekitException("non-chronological dates in file {0}, line {1}",
                                                  new Object[] {
                                                      getFile().getAbsolutePath(),
                                                      Integer.valueOf(lineNumber)
                                                  });
                    }
                    foundEntries = true;
                    entries.put(leapDay, offset);

                } catch (NumberFormatException nfe) {
                    throw new OrekitException("unable to parse line {0} in IERS UTC-TAI history file {1}",
                                              new Object[] {
                                                  Integer.valueOf(lineNumber),
                                                  getFile().getAbsolutePath()
                                              });
                }
            }
        }

        if (!foundEntries) {
            throw new OrekitException("no entries found in IERS UTC-TAI history file {0}",
                                      new Object[] {
                                          getFile().getAbsolutePath()
                                      });
        }

    }

}