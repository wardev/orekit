package fr.cs.orekit.frames;

import org.apache.commons.math.geometry.Rotation;
import org.apache.commons.math.geometry.Vector3D;

import fr.cs.orekit.errors.OrekitException;
import fr.cs.orekit.time.AbsoluteDate;

/** International Terrestrial Reference Frame 2000.
 * <p> Handles pole motion effects and depends on {@link TIRF2000Frame}, its
 * parent frame .</p>
 * @author Luc Maisonobe
 */
class ITRF2000Frame extends Frame {

    /** Serializable UID. */
    private static final long serialVersionUID = 720487682019109221L;

    /** 2&pi;. */
    private static final double twoPi = 2.0 * Math.PI;

    /** Radians per arcsecond. */
    private static final double radiansPerArcsecond = twoPi / 1296000;

    /** Julian century per second. */
    private static final double julianCenturyPerSecond = 1.0 / (36525.0 * 86400.0);

    /** S' rate in radians per julian century.
     * Approximately -47 microarcsecond per julian century (Lambert and Bizouard, 2002)
     */
    private static final double sPrimeRate = -47e-6 * radiansPerArcsecond ;

    /** Cached date to avoid useless calculus */
    private AbsoluteDate cachedDate;

    /** Constructor for the singleton.
     * @param parent the TIRF2000
     * @param date the current date
     * @param name the string reprensentation
     * @throws OrekitException
     */
    protected ITRF2000Frame(Frame parent, AbsoluteDate date, String name) throws OrekitException {
        super(parent, null, name);
        // everything is in place, we can now synchronize the frame
        updateFrame(date);
    }

    /** Update the frame to the given date.
     * <p>The update considers the pole motion from IERS data.</p>
     * @param date new value of the date
     * @exception OrekitException if the nutation model data embedded in the
     * library cannot be read
     */
    protected void updateFrame(AbsoluteDate date) throws OrekitException {

        if (cachedDate == null||cachedDate!=date) {

            //    offset from J2000 epoch in julian centuries
            final double tts = date.minus(AbsoluteDate.J2000Epoch);
            final double ttc =  tts * julianCenturyPerSecond;

            // get the current IERS pole correction parameters
            final PoleCorrection iCorr = EarthOrientationHistory.getInstance().getPoleCorrection(date);

            // compute the additional terms not included in IERS data
            final PoleCorrection tCorr = tidalCorrection(date);
            final PoleCorrection nCorr = nutationCorrection(date);

            // elementary rotations due to pole motion in terrestrial frame
            final Rotation r1 = new Rotation(Vector3D.plusI, -(iCorr.yp + tCorr.yp + nCorr.yp));
            final Rotation r2 = new Rotation(Vector3D.plusJ, -(iCorr.xp + tCorr.xp + nCorr.xp));
            final Rotation r3 = new Rotation(Vector3D.plusK, sPrimeRate * ttc);

            // complete pole motion in terrestrial frame
            final Rotation wRot = r3.applyTo(r2.applyTo(r1));

            // combined effects
            final Rotation combined = wRot.revert();

            // set up the transform from parent GCRS (J2000) to ITRF
            updateTransform(new Transform(combined , Vector3D.zero));
            cachedDate = date;
        }
    }

    /** Compute tidal correction to the pole motion.
     * @param date current date
     * @return tidal correction
     */
    private PoleCorrection tidalCorrection(AbsoluteDate date) {
        // TODO compute tidal correction to pole motion
        return PoleCorrection.NULL_CORRECTION;
    }

    /** Compute nutation correction due to tidal gravity.
     * @param date current date
     * @return nutation correction
     */
    private PoleCorrection nutationCorrection(AbsoluteDate date) {
        // this factor seems to be of order of magnitude a few tens of
        // micro arcseconds. It is computed from the classical approach
        // (not the new one used here) and hence requires computation
        // of GST, IAU2000A nutation, equations of equinoxe ...
        // For now, this term is ignored
        return PoleCorrection.NULL_CORRECTION;
    }

}