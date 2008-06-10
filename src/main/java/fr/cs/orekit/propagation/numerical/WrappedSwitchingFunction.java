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
package fr.cs.orekit.propagation.numerical;

import org.apache.commons.math.ode.SwitchException;
import org.apache.commons.math.ode.SwitchingFunction;

import fr.cs.orekit.attitudes.AttitudeLaw;
import fr.cs.orekit.errors.OrekitException;
import fr.cs.orekit.frames.Frame;
import fr.cs.orekit.orbits.EquinoctialOrbit;
import fr.cs.orekit.propagation.SpacecraftState;
import fr.cs.orekit.time.AbsoluteDate;

/** Converts OREKIT switching functions to commons-math interface. */
class WrappedSwitchingFunction implements SwitchingFunction {

    /** Serializable UID. */
    private static final long serialVersionUID = 4547309170150559639L;

    /** Underlying Orekit switching function. */
    private final OrekitSwitchingFunction swf;

    /** Reference date from which t is counted. */
    private final AbsoluteDate referenceDate;

    /** Central body attraction coefficient (m<sup>3</sup>/s<sup>2</sup>). */
    private final double mu;

    /** integrationFrame frame in which integration is performed. */
    private final Frame integrationFrame;

    /** attitudeLaw spacecraft attitude law. */
    private final AttitudeLaw attitudeLaw;

    /** Build a wrapped switching function.
     * @param swf Orekit switching function
     * @param referenceDate reference date from which t is counted
     * @param mu central body attraction coefficient (m<sup>3</sup>/s<sup>2</sup>)
     * @param integrationFrame frame in which integration is performed
     * @param attitudeLaw spacecraft attitude law
 * @version $Revision$ $Date$
     */
    public WrappedSwitchingFunction(final OrekitSwitchingFunction swf,
                                    final AbsoluteDate referenceDate, final double mu,
                                    final Frame integrationFrame, final AttitudeLaw attitudeLaw) {
        this.swf              = swf;
        this.referenceDate    = referenceDate;
        this.mu               = mu;
        this.integrationFrame = integrationFrame;
        this.attitudeLaw      = attitudeLaw;
    }

    /** {@inheritDoc} */
    public double g(final double t, final double[] y)
        throws SwitchException {
        try {
            return swf.g(mapState(t, y, referenceDate, mu, integrationFrame, attitudeLaw));
        } catch (OrekitException oe) {
            throw new SwitchException(oe);
        }
    }

    /** {@inheritDoc} */
    public int eventOccurred(final double t, final double[] y)
        throws SwitchException {
        try {
            swf.eventOccurred(mapState(t, y, referenceDate, mu, integrationFrame, attitudeLaw));
            return RESET_DERIVATIVES;
        } catch (OrekitException oe) {
            throw new SwitchException(oe);
        }
    }

    /** {@inheritDoc} */
    public void resetState(final double t, final double[] y) {
        // never called since eventOccured never returns CallResetState
    }

    /** Convert state array to space dynamics objects
     * ({@link fr.cs.orekit.time.AbsoluteDate AbsoluteDate} and
     * ({@link fr.cs.orekit.orbits.Orbit OrbitalParameters}).
     * @param t integration time (s)
     * @param y state as a flat array
     * @param referenceDate reference date from which t is counted
     * @param mu central body attraction coefficient (m<sup>3</sup>/s<sup>2</sup>)
     * @param integrationFrame frame in which integration is performed
     * @param attitudeLaw spacecraft attitude law
     * @return state corresponding to the flat array as a space dynamics object
     * @exception OrekitException if attitude law cannot provide state
     */
    private static SpacecraftState mapState(final double t, final double [] y,
                                            final AbsoluteDate referenceDate, final double mu,
                                            final Frame integrationFrame,
                                            final AttitudeLaw attitudeLaw)
        throws OrekitException {

        // update space dynamics view
        final AbsoluteDate currentDate = new AbsoluteDate(referenceDate, t);
        final EquinoctialOrbit currentOrbit =
            new EquinoctialOrbit(y[0], y[1],y[2],y[3],y[4],y[5],
                                      EquinoctialOrbit.TRUE_LATITUDE_ARGUMENT,
                                      integrationFrame, currentDate, mu);
        return
            new SpacecraftState(currentOrbit, y[6],
                                attitudeLaw.getState(currentDate,
                                                     currentOrbit.getPVCoordinates(),
                                                     integrationFrame));
    }

}
