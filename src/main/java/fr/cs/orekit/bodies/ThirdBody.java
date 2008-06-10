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
package fr.cs.orekit.bodies;

import java.io.Serializable;

import fr.cs.orekit.errors.OrekitException;
import fr.cs.orekit.frames.Frame;
import fr.cs.orekit.time.AbsoluteDate;
import org.apache.commons.math.geometry.Vector3D;

/** This class represents an attracting body different from the central one.
 * @author Édouard Delente
 * @version $Revision$ $Date$
 */

public abstract class ThirdBody implements CelestialBody, Serializable {

    /** Reference radius. */
    private double radius;

    /** Attraction coefficient. */
    private double mu;

    /** Simple constructor.
     * @param radius reference radius
     * @param mu attraction coefficient
     */
    protected ThirdBody(final double radius, final double mu) {
        this.radius = radius;
        this.mu = mu;
    }

    /** {@inheritDoc} */
    public abstract Vector3D getPosition(AbsoluteDate date, Frame frame)
        throws OrekitException;

    /** Get the reference radius of the body.
     * @return reference radius of the body (m)
     */
    public double getRadius() {
        return radius;
    }

    /** Get the attraction coefficient of the body.
     * @return attraction coefficient of the body (m<sup>3</sup>/s<sup>2</sup>)
     */
    public double getMu() {
        return mu;
    }

}
