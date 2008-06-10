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

import java.io.Serializable;

import org.apache.commons.math.ode.DerivativeException;
import org.apache.commons.math.ode.StepInterpolator;

import fr.cs.orekit.attitudes.AttitudeLaw;
import fr.cs.orekit.errors.OrekitException;
import fr.cs.orekit.errors.PropagationException;
import fr.cs.orekit.frames.Frame;
import fr.cs.orekit.orbits.EquinoctialOrbit;
import fr.cs.orekit.orbits.Orbit;
import fr.cs.orekit.propagation.SpacecraftState;
import fr.cs.orekit.time.AbsoluteDate;

/** This class is a space-dynamics aware step interpolator.
 *
 * <p>It mirrors the {@link org.apache.commons.math.ode.StepInterpolator
 * StepInterpolator} interface from <a href="http://commons.apache.org/math/">
 * commons-math</a> but provides a space-dynamics interface to the methods.</p>
 *
 * @version $Revision$ $Date$
 */
public class OrekitStepInterpolator implements Serializable {

    /** Serializable UID. */
    private static final long serialVersionUID = 7332721584479827727L;

    /** Reference date. */
    private final AbsoluteDate reference;

    /** Reference frame. */
    private final Frame frame;

    /** Central body attraction coefficient. */
    private final double mu;

    /** Attitude law. */
    private final AttitudeLaw attitudeLaw;

    /** Underlying non-space-dynamics aware interpolator. */
    private final StepInterpolator interpolator;

    /** Build an interpolator.
     * @param reference reference date
     * @param frame reference frame
     * @param mu central body attraction coefficient
     * @param attitudeLaw attitude law
     * @param interpolator underlying non space dynamics interpolator
     */
    public OrekitStepInterpolator(final AbsoluteDate reference, final Frame frame,
                                  final double mu, final AttitudeLaw attitudeLaw,
                                  final StepInterpolator interpolator) {
        this.reference    = reference;
        this.frame        = frame;
        this.mu           = mu;
        this.attitudeLaw  = attitudeLaw;
        this.interpolator = interpolator;
    }

    /** Get the current grid date.
     * @return current grid date
     */
    public AbsoluteDate getCurrentDate() {
        return new AbsoluteDate(reference, interpolator.getCurrentTime());
    }

    /** Get the previous grid date.
     * @return previous grid date
     */
    public AbsoluteDate getPreviousDate() {
        return new AbsoluteDate(reference, interpolator.getPreviousTime());
    }

    /** Get the interpolated date.
     * <p>If {@link #setInterpolatedDate(AbsoluteDate) setInterpolatedDate}
     * has not been called, the date returned is the same as  {@link
     * #getCurrentDate() getCurrentDate}.</p>
     * @return interpolated date
     * @see #setInterpolatedDate(AbsoluteDate)
     * @see #getInterpolatedState()
     */
    public AbsoluteDate getInterpolatedDate() {
        return new AbsoluteDate(reference, interpolator.getInterpolatedTime());
    }

    /** Set the interpolated date.
     * <p>It is possible to set the interpolation date outside of the current
     * step range, but accuracy will decrease as date is farther.</p>
     * @param date interpolated date to set
     * @exception PropagationException if underlying interpolator cannot handle
     * the date
     * @see #getInterpolatedDate()
     * @see #getInterpolatedState()
     */
    public void setInterpolatedDate(final AbsoluteDate date)
        throws PropagationException {
        try {
            interpolator.setInterpolatedTime(date.minus(reference));
        } catch (DerivativeException de) {
            throw new PropagationException(de.getMessage(), de);
        }
    }

    /** Get the interpolated state.
     * @return interpolated state at the current interpolation date
     * @exception OrekitException if state cannot be interpolated or converted
     * @see #getInterpolatedDate()
     * @see #setInterpolatedDate(AbsoluteDate)
     */
    public SpacecraftState getInterpolatedState() throws OrekitException {
        final double[] y = interpolator.getInterpolatedState();
        final AbsoluteDate current = new AbsoluteDate(reference, interpolator.getCurrentTime());
        final Orbit orbit =
            new EquinoctialOrbit(y[0], y[1], y[2], y[3], y[4], y[5],
                                      EquinoctialOrbit.TRUE_LATITUDE_ARGUMENT,
                                      frame, current, mu);
        return new SpacecraftState(orbit, y[6],
                                   attitudeLaw.getState(current, orbit.getPVCoordinates(), frame));
    }

    /** Check is integration direction is forward in date.
     * @return true if integration is forward in date
     */
    public boolean isForward() {
        return interpolator.isForward();
    }

}
