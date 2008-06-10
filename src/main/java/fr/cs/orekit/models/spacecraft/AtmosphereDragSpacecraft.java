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
package fr.cs.orekit.models.spacecraft;

import org.apache.commons.math.geometry.Vector3D;

/** Adapted container for the Atmosphere drag force model.
 *
 * @see fr.cs.orekit.propagation.numerical.forces.perturbations.AtmosphericDrag
 * @author Fabien Maussion
 * @version $Revision$ $Date$
 */
public interface AtmosphereDragSpacecraft {

    /** Get the visible surface from a specific direction.
     * See {@link fr.cs.orekit.propagation.numerical.forces.perturbations.AtmosphericDrag} for more explanations.
     * @param direction direction of the flux in the spacecraft frame
     * @return surface (m<sup>2</sup>)
     */
    double getSurface(Vector3D direction);

    /** Get the drag coefficients vector.
     * See {@link fr.cs.orekit.propagation.numerical.forces.perturbations.AtmosphericDrag} for more explanations.
     * @param direction direction of the flux in the spacecraft frame
     * @return drag coefficients vector (defined in the spacecraft frame)
     */
    Vector3D getDragCoef(Vector3D direction);

}
