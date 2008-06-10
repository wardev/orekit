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
package fr.cs.orekit.attitudes;

import org.apache.commons.math.geometry.Vector3D;

import fr.cs.orekit.errors.OrekitException;
import fr.cs.orekit.frames.Frame;
import fr.cs.orekit.time.AbsoluteDate;
import fr.cs.orekit.bodies.BodyShape;
import fr.cs.orekit.bodies.GeodeticPoint;
import fr.cs.orekit.utils.PVCoordinates;


/**
 * This class handles nadir pointing attitude law.

 * <p>
 * This class represents the attitude law where the satellite z axis is
 * pointing to the vertical of the ground point under satellite.</p>
 * <p>
 * The object <code>NadirPointing</code> is guaranteed to be immutable.
 * </p>
 * @see     GroundPointing
 * @author Véronique Pommier-Maurussane
 * @version $Revision$ $Date$
 */
public class NadirPointing extends GroundPointing {

    /** Serializable UID. */
    private static final long serialVersionUID = 9077899256315179822L;

    /** Body shape.  */
    private final BodyShape shape;

    /** Creates new instance.
     * @param shape Body shape
     */
    public NadirPointing(final BodyShape shape) {
        // Call constructor of superclass
        super(shape.getBodyFrame());
        this.shape = shape;
    }

    /** Get target expressed in body frame at given date.
     * @param date computation date.
     * @param pv satellite position-velocity vector at given date in given frame.
     * @param frame Frame in which satellite position-velocity is given.
     * @return target position/velocity in body frame
     * @throws OrekitException if some specific error occurs
     *
     * <p>User should check that position/velocity and frame is consistent with given frame.
     * </p>
     */
    protected PVCoordinates getTargetInBodyFrame(final AbsoluteDate date,
                                                 final PVCoordinates pv, final Frame frame)
        throws OrekitException {

        // Satellite position in geodetic coordinates
        final GeodeticPoint gpSat = shape.transform(pv.getPosition(), frame, date);

        // Ground point under satellite vertical
        final GeodeticPoint gpGround =
            new GeodeticPoint(gpSat.getLongitude(), gpSat.getLatitude(), 0.0);

        // Return target = this intersection point, with null velocity
        return new PVCoordinates(shape.transform(gpGround), Vector3D.ZERO);
    }

}
