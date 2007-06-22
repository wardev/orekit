package fr.cs.aerospace.orekit.iers;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.cs.aerospace.orekit.errors.OrekitException;
import fr.cs.aerospace.orekit.frames.PoleCorrection;

/** Loader for bulletin B files.
 * <p>Bulletin B files contain {@link EarthOrientationParameters
 * Earth Orientation Parameters} for a few months periods.</p>
 * <p>The bulletin B files are recognized thanks to their base names,
 * which must match the pattern <code>bulletinb_IAU2000-###.txt</code>
 * (or <code>bulletinb_IAU2000-###.txt.gz</code> for gzip-compressed files)
 * where # stands for a digit character.</p>
 * @author Luc Maisonobe
 */
public class BulletinBFilesLoader extends IERSFileVisitor {

  public BulletinBFilesLoader() {

    super("^bulletinb_IAU2000-(\\d\\d\\d)\\.txt(?:\\.gz)?$");

    // the section headers lines in the bulletin B monthly data files have
    // the following form (the indentation discrepancy for section 6 is
    // really present in the available files):
    // 1 - EARTH ORIENTATION PARAMETERS (IERS evaluation).
    // 2 - SMOOTHED VALUES OF x, y, UT1, D, dX, dY (IERS EVALUATION)
    // 3 - NORMAL VALUES OF THE EARTH ORIENTATION PARAMETERS AT FIVE-DAY INTERVALS 
    // 4 - DURATION OF THE DAY AND ANGULAR VELOCITY OF THE EARTH (IERS evaluation).
    // 5 - INFORMATION ON TIME SCALES 
    //       6 - SUMMARY OF CONTRIBUTED EARTH ORIENTATION PARAMETERS SERIES
    sectionHeaderPattern =
      Pattern.compile("^ +([123456]) - \\p{Upper}+ \\p{Upper}+ \\p{Upper}+.*");

    // the markers bracketing the final values in section 1 have the following form:
    //  Final Bulletin B values.
    //   ...
    //  Preliminary extension, to be updated weekly in Bulletin A and monthly
    //  in Bulletin B.
    finalValuesStartPattern = Pattern.compile("^\\p{Blank}+Final Bulletin B values.*");
    finalValuesEndPattern   = Pattern.compile("^\\p{Blank}+Preliminary extension,.*");

    // the data lines in the bulletin B monthly data files have the following form:
    // in section 1:
    // FEB   3  53769   0.05025  0.38417  0.322391  -32.677609    0.06   -0.31
    // FEB   8  53774   0.05153  0.38430  0.317918  -32.682082    0.28   -0.41
    // in section 2:
    // FEB   3   53769  0.05025  0.38417  0.321173 -1.218   1.507   0.06  -0.31
    // FEB   4   53770  0.05015  0.38454  0.319753 -1.748   1.275   0.03  -0.35
    String monthField       = "\\p{Blank}*\\p{Upper}\\p{Upper}\\p{Upper}";
    String dayField         = "\\p{Blank}+[ 0-9]\\p{Digit}";
    String mjdField         = "\\p{Blank}+(\\p{Digit}\\p{Digit}\\p{Digit}\\p{Digit}\\p{Digit})";
    String storedRealField  = "\\p{Blank}+(-?\\p{Digit}+\\.(?:\\p{Digit})+)";
    String ignoredRealField = "\\p{Blank}+-?\\p{Digit}+\\.(?:\\p{Digit})+";
    section1DataPattern = Pattern.compile("^" + monthField + dayField + mjdField
                                          + ignoredRealField + ignoredRealField + ignoredRealField
                                          + ignoredRealField + ignoredRealField + ignoredRealField
                                          + "\\p{Blank}*$");
    section2DataPattern = Pattern.compile("^" + monthField + dayField + mjdField
                                          + storedRealField  + storedRealField  + storedRealField
                                          + ignoredRealField + ignoredRealField
                                          + ignoredRealField + ignoredRealField
                                          + "\\p{Blank}*$");

  }

  /** Load Earth Orientation Parameters.
   * <p>The data is concatenated from all bulletin B data files
   * which can be found in the configured IERS directory.</p>
   * @param eop set where to <em>add</em> EOP data (pre-existing
   * data is preserved)
   * @exception OrekitException if some data can't be read or some
   * file content is corrupted
   */
  public void loadEOP(TreeSet eop)
    throws OrekitException {
    this.eop = eop;
    new IERSDirectoryCrawler().crawl(this);
  }

  protected void visit(BufferedReader reader)
  throws OrekitException, IOException {

    // Extract mjd bounds from section 1
    int mjdMin = -1;
    int mjdMax = -1;
    boolean inFinalValuesPart = false;
    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
      Matcher matcher = finalValuesStartPattern.matcher(line);
      if (matcher.matches()) {
        // we are entering final values part (in section 1)
        inFinalValuesPart = true;
      } else if (inFinalValuesPart) {
        matcher = section1DataPattern.matcher(line);
        if (matcher.matches()) {
          // this is a data line, build an entry from the extracted fields
          int mjd = Integer.parseInt(matcher.group(1));
          if (mjdMin < 0) {
            mjdMin = mjd;
          } else {
            mjdMax = mjd;
          }
        } else {
          matcher = finalValuesEndPattern.matcher(line);
          if (matcher.matches()) {
            // we leave final values part
            break;
          }
        }
      }
    }

    // read the data lines in the final values part inside section 2
    boolean inSection2 = false;
    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
      Matcher matcher = sectionHeaderPattern.matcher(line);
      if (matcher.matches() && "2".equals(matcher.group(1))) {
        // we are entering section 2
        inSection2 = true;
      } else if (inSection2) {
        matcher = section2DataPattern.matcher(line);
        if (matcher.matches()) {
          // this is a data line, build an entry from the extracted fields
          int    date = Integer.parseInt(matcher.group(1));
          double x    = Double.parseDouble(matcher.group(2)) * arcSecondsToRadians;
          double y    = Double.parseDouble(matcher.group(3)) * arcSecondsToRadians;
          double dtu1 = Double.parseDouble(matcher.group(4));
          if (date >= mjdMin) {
            eop.add(new EarthOrientationParameters(date, dtu1, new PoleCorrection(x, y)));
            if (date >= mjdMax) {
              // don't bother reading the rest of the file
              return;
            }
          }
        }
      }
    }

  }

  /** Section header pattern. */
  private Pattern sectionHeaderPattern;

  /** Patterns in section 1. */
  private Pattern section1DataPattern;
  private Pattern finalValuesStartPattern;
  private Pattern finalValuesEndPattern;

  /** Data line pattern in section 2. */
  private Pattern section2DataPattern;

  /** Conversion factor. */
  private double arcSecondsToRadians;

  /** Earth Orientation Parameters entries. */
  private TreeSet eop;

}
