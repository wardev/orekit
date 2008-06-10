/* Copyright 2002-2008 CS Communication & Systèmes
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.cs.orekit.iers;

import java.io.BufferedReader;

import fr.cs.orekit.errors.OrekitException;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class IERSDirectoryCrawlerTest extends TestCase {

    public void testNoDirectory() {
        checkFailure("inexistant-directory");
    }

    public void testNotADirectory() {
        checkFailure("regular-data/UTC-TAI.history.gz");
    }

    private void checkFailure(String directoryName) {
        try {
            System.setProperty(IERSDirectoryCrawler.IERS_ROOT_DIRECTORY, directoryName);
            new IERSDirectoryCrawler().crawl(new DoNothingCrawler(".*"));
            fail("an exception should have been thrown");
        } catch (OrekitException e) {
            // expected behavior
        } catch (Exception e) {
            e.printStackTrace();
            fail("wrong exception caught");
        }
    }

    private static class DoNothingCrawler extends IERSFileCrawler {
        public DoNothingCrawler(String pattern) {
            super(pattern);
        }
        protected void visit(BufferedReader reader) {
            // do nothing
        }
    }

    public static Test suite() {
        return new TestSuite(IERSDirectoryCrawlerTest.class);
    }

}
