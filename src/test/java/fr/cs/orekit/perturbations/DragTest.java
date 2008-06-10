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
package fr.cs.orekit.perturbations;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.commons.math.geometry.Vector3D;

import fr.cs.orekit.Utils;
import fr.cs.orekit.bodies.OneAxisEllipsoid;
import fr.cs.orekit.errors.OrekitException;
import fr.cs.orekit.frames.Frame;
import fr.cs.orekit.frames.Transform;
import fr.cs.orekit.iers.IERSDirectoryCrawler;
import fr.cs.orekit.models.perturbations.SimpleExponentialAtmosphere;
import fr.cs.orekit.time.AbsoluteDate;

public class DragTest extends TestCase {

    public DragTest(String name) {
        super(name);
    }

    public void testExpAtmosphere() throws OrekitException {
        Vector3D posInJ2000 = new Vector3D(10000,Vector3D.PLUS_I);
        AbsoluteDate date = AbsoluteDate.J2000_EPOCH;
        Frame itrf = Frame.getReferenceFrame(Frame.ITRF2000B, date);
        SimpleExponentialAtmosphere atm =
            new SimpleExponentialAtmosphere(new OneAxisEllipsoid(Utils.ae, 1.0 / 298.257222101, itrf),
                                            itrf, 0.0004, 42000.0, 7500.0);
        Vector3D vel = atm.getVelocity(date, posInJ2000, Frame.getJ2000());

        Transform toBody = Frame.getJ2000().getTransformTo(itrf, date);
        Vector3D test = Vector3D.crossProduct(toBody.getRotationRate(),posInJ2000);
        test = test.subtract(vel);
        assertEquals(0, test.getNorm(), 2.9e-5);

    }

    public void setUp() {
        System.setProperty(IERSDirectoryCrawler.IERS_ROOT_DIRECTORY, "regular-data");
    }

    public static Test suite() {
        return new TestSuite(DragTest.class);
    }

}
