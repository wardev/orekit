package fr.cs.orekit.propagation.numerical.forces.perturbations;

import org.apache.commons.math.geometry.Vector3D;
import fr.cs.orekit.bodies.ThirdBody;
import fr.cs.orekit.errors.OrekitException;
import fr.cs.orekit.frames.Frame;
import fr.cs.orekit.models.spacecraft.SolarRadiationPressureSpacecraft;
import fr.cs.orekit.propagation.SpacecraftState;
import fr.cs.orekit.propagation.numerical.OrekitSwitchingFunction;
import fr.cs.orekit.propagation.numerical.TimeDerivativesEquations;
import fr.cs.orekit.propagation.numerical.forces.ForceModel;
import fr.cs.orekit.time.AbsoluteDate;
import fr.cs.orekit.utils.PVCoordinates;

/** Solar radiation pressure force model.
 *
 * @author F. Maussion
 * @author E.Delente
 */
public class SolarRadiationPressure implements ForceModel {

    /** Serializable UID. */
    private static final long serialVersionUID = -3831043970561319297L;

    /** Error message for too low trajectory. */
    private static final String LOW_TRAJECTORY_MESSAGE =
        "trajectory inside the Brillouin sphere (r = {0})";

    /** Reference distance (m). */
    private final double dRef;

    /** Reference radiation pressure at dRef (N/m<sup>2</sup>).*/
    private final double pRef;

    /** Sun model. */
    private final ThirdBody sun;

    /** Earth model. */
    private final double equatorialRadius;

    /** Spacecraft. */
    private final SolarRadiationPressureSpacecraft spacecraft;

    /** Simple constructor with default reference values.
     * <p>When this constructor is used, the reference values are:</p>
     * <ul>
     *   <li>d<sub>ref</sub> = 149597870000.0 m</li>
     *   <li>p<sub>ref</sub> = 4.56 10<sup>-6</sup> N/m<sup>2</sup></li>
     * </ul>
     * @param sun Sun model
     * @param equatorialRadius spherical shape model (for umbra/penumbra computation)
     * @param spacecraft the object physical and geometrical information
     */
    public SolarRadiationPressure(ThirdBody sun, double equatorialRadius,
                                  SolarRadiationPressureSpacecraft spacecraft) {
        this(149597870000.0, 4.56e-6, sun, equatorialRadius, spacecraft);
    }

    /** Complete constructor.
     * @param dRef reference distance for the radiation pressure (m)
     * @param pRef reference radiation pressure at dRef (N/m<sup>2</sup>)
     * @param sun Sun model
     * @param equatorialRadius spherical shape model (for umbra/penumbra computation)
     * @param spacecraft the object physical and geometrical information
     */
    public SolarRadiationPressure(double dRef, double pRef, ThirdBody sun,
                                  double equatorialRadius, SolarRadiationPressureSpacecraft spacecraft) {
        this.dRef  = dRef;
        this.pRef  = pRef;
        this.sun   = sun;
        this.equatorialRadius = equatorialRadius;
        this.spacecraft = spacecraft;
    }

    /** Compute the contribution of the solar radiation pressure to the perturbing
     * acceleration.
     * @param s the current state information : date, cinematics, attitude
     * @param adder object where the contribution should be added
     * @param mu central gravitation coefficient
     * @exception OrekitException if some specific error occurs
     */
    public void addContribution(SpacecraftState s, TimeDerivativesEquations adder, double mu)
        throws OrekitException {
        // raw radiation pressure
        final Vector3D satSunVector =
            sun.getPosition(s.getDate() ,
                            s.getFrame()).subtract(s.getPVCoordinates(mu).getPosition());

        final double dRatio = dRef / satSunVector.getNorm();
        final double rawP   =
            pRef * dRatio * dRatio * getLightningRatio(s.getPVCoordinates(mu).getPosition(),
                                                       s.getFrame(), s.getDate());

        // spacecraft characteristics effects
        final Vector3D u = satSunVector.normalize();
        final Vector3D inSpacecraft = s.getAttitudeKinematics().getAttitude().applyTo(u);
        final double kd = (1.0 - spacecraft.getAbsCoef(inSpacecraft).getNorm()) *
            (1.0 - spacecraft.getReflectionCoef(inSpacecraft).getNorm());

        final double acceleration =
            rawP * (1 + kd * 4.0 / 9.0 ) * spacecraft.getSurface(inSpacecraft) / s.getMass();

        // provide the perturbing acceleration to the derivatives adder
        adder.addXYZAcceleration(acceleration * u.getX(),
                                 acceleration * u.getY(),
                                 acceleration * u.getZ());

    }

    /** Get the lightning ratio ([0-1]).
     * @param position the satellite's position in the selected frame.
     * @param frame in which is defined the position
     * @param date the date
     * @return lightning ratio
     * @exception OrekitException if the trajectory is inside the Earth
     */
    public double getLightningRatio(Vector3D position, Frame frame, AbsoluteDate date)
        throws OrekitException {
        final Vector3D satSunVector = sun.getPosition(date, frame).subtract(position);
        // Earth apparent radius
        final double r = position.getNorm();
        if (r <= equatorialRadius) {
            throw new OrekitException(LOW_TRAJECTORY_MESSAGE, new Object[] { new Double(r) });
        }

        final double alphaEarth = Math.atan(equatorialRadius / r);

        // Definition of the Sun's apparent radius
        final double alphaSun = sun.getRadius() / satSunVector.getNorm();

        // Retrieve the Sat-Sun / Sat-Central body angle
        final double sunEarthAngle = Vector3D.angle(satSunVector, position.negate());

        double result = 1.0;

        // Is the satellite in complete penumbra ?
        if (sunEarthAngle - alphaEarth + alphaSun < 0.0) {
            result = 0.0;
        }
        // Compute a lightning ratio in penumbra
        if ((sunEarthAngle - alphaEarth + alphaSun >= 0.0)&&((sunEarthAngle - alphaEarth - alphaSun <= 0.0))) {

            //result = (alphaSun + sunEarthAngle - alphaEarth) / (2*alphaSun);

            final double alpha1 =
                (sunEarthAngle * sunEarthAngle -
                        (alphaEarth - alphaSun) * (alphaSun + alphaEarth)) / (2 * sunEarthAngle);

            final double alpha2 =
                (sunEarthAngle * sunEarthAngle +
                        (alphaEarth - alphaSun) * (alphaSun + alphaEarth)) / (2 * sunEarthAngle);

            final double P1 = Math.PI * alphaSun * alphaSun -
                alphaSun * alphaSun * Math.acos(alpha1 / alphaSun) +
                alpha1 * Math.sqrt(alphaSun * alphaSun - alpha1 * alpha1);

            final double P2 = alphaEarth * alphaEarth * Math.acos(alpha2 / alphaEarth) -
                alpha2 * Math.sqrt(alphaEarth * alphaEarth - alpha2 * alpha2);

            result = (P1 - P2) / (Math.PI * alphaSun * alphaSun);


        }

        return result;

    }

    /** Get the switching functions related to umbra and penumbra passes.
     * @return umbra/penumbra switching functions
     */
    public OrekitSwitchingFunction[] getSwitchingFunctions() {
        return new OrekitSwitchingFunction[] { new UmbraSwitch(), new PenumbraSwitch() };
    }

    /** This class defines the Umbra switching function.
     * It triggers when the satellite enters the umbra zone.
     */
    private class UmbraSwitch implements OrekitSwitchingFunction {

        /** Serializable UID. */
        private static final long serialVersionUID = 8164370576237170346L;

        public void eventOccurred(SpacecraftState s, double mu) {
            // do nothing
        }

        /** The G-function is the difference between the Sat-Sun-Sat-Earth angle and
         * the Earth's apparent radius
         * @param s the current state information : date, cinematics, attitude
         * @param mu central gravitation coefficient
         * @exception OrekitException if sun or spacecraft position cannot be computed
         */
        public double g(SpacecraftState s, double mu)
            throws OrekitException {
            final PVCoordinates pv = s.getPVCoordinates(mu);
            final Vector3D satSunVector =
                sun.getPosition(s.getDate(), s.getFrame()).subtract(pv.getPosition());
            final double sunEarthAngle = Math.PI - Vector3D.angle(satSunVector, pv.getPosition());
            final double r = pv.getPosition().getNorm();
            if (r <= equatorialRadius) {
                throw new OrekitException(LOW_TRAJECTORY_MESSAGE, new Object[] { new Double(r) });
            }
            final double alphaEarth = equatorialRadius / r;
            return sunEarthAngle - alphaEarth;
        }

        /** {@inheritDoc} */
        public double getMaxCheckInterval() {
            // we accept losing umbra passes shorter than one minute
            return 60.0;
        }

        /** {@inheritDoc} */
        public double getThreshold() {
            // convergence threshold in seconds for umbra events
            return 1.0e-3;
        }

        /** {@inheritDoc} */
        public int getMaxIterationCount() {
            return 100;
        }

    }

    /** This class defines the penumbra switching function.
     * It triggers when the satellite enters the penumbra zone.
     */
    private class PenumbraSwitch implements OrekitSwitchingFunction {

        /** Serializable UID. */
        private static final long serialVersionUID = -8548885301322210937L;

        /** {@inheritDoc} */
        public void eventOccurred(SpacecraftState s, double mu) {
            // do nothing
        }

        /** The G-function is the difference between the Sat-Sun-Sat-Earth angle and
         * the sum of the Earth's and Sun's apparent radius
         * @param s the current state information : date, cinematics, attitude
         * @param mu central gravitation coefficient
         * @exception OrekitException if sun or spacecraft position cannot be computed
         */
        public double g(SpacecraftState s, double mu)
            throws OrekitException {
            final PVCoordinates pv = s.getPVCoordinates(mu);
            final Vector3D satSunVector =
                sun.getPosition(s.getDate() , s.getFrame()).subtract(pv.getPosition());
            final double sunEarthAngle = Math.PI - Vector3D.angle(satSunVector, pv.getPosition());
            final double r = pv.getPosition().getNorm();
            if (r <= equatorialRadius) {
                throw new OrekitException(LOW_TRAJECTORY_MESSAGE, new Object[] { new Double(r) });
            }
            final double alphaEarth = equatorialRadius / r;
            final double alphaSun   = sun.getRadius() / satSunVector.getNorm();
            return sunEarthAngle - alphaEarth - alphaSun;
        }

        /** {@inheritDoc} */
        public double getMaxCheckInterval() {
            // we accept losing penumbra passes shorter than one minute
            return 60.0;
        }

        /** {@inheritDoc} */
        public double getThreshold() {
            // convergence threshold in seconds for penumbra events
            return 1.0e-3;
        }

        /** {@inheritDoc} */
        public int getMaxIterationCount() {
            return 100;
        }

    }

}