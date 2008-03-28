package fr.cs.orekit.orbits;

import org.apache.commons.math.geometry.Vector3D;
import fr.cs.orekit.frames.Frame;
import fr.cs.orekit.utils.PVCoordinates;
import java.io.Serializable;

/**
 * This class handles orbital parameters without date.

 * <p>
 * The aim of this class is to separate the orbital parameters from the date
 * for cases where dates are managed elsewhere. This occurs for example during
 * numerical integration and interpolation because date is the free parameter
 * whereas the orbital parameters are bound to either differential or
 * interpolation equations.</p>

 * <p>
 * For user convenience, both the cartesian and the equinoctial elements
 * are provided by this class, regardless of the canonical representation
 * implemented in the derived class (which may be classical keplerian
 * elements for example).
 * </p>
 * <p>
 * The parameters are defined in a frame specified by the user. It is important
 * to make sure this frame is coherent : it probably is inertial and centered
 * on the central body. This information is used for example by some
 * force models.
 * </p>
 * <p>
 * The object <code>OrbitalParameters</code> is guaranted to be immutable.
 * </p>
 * @see     Orbit
 * @version $Id:OrbitalParameters.java 1310 2007-07-05 16:04:25Z luc $
 * @author  L. Maisonobe
 * @author  G. Prat
 * @author  F.Maussion
 */
public abstract class OrbitalParameters implements Serializable {

    /** Frame in which are defined the orbital parameters */
    protected final Frame frame;

    /** Last value of mu used to compute position and velocity (m<sup>3</sup>/s<sup>2</sup>). */
    private double cachedMu;

    /** Last computed PVCoordinates. */
    private PVCoordinates cachedPVCoordinates;

    /** Indicator for dirty PVCoordinates cache. */
    private boolean dirtyCache;

    /** Default constructor.
     * Build a new instance with arbitrary default elements.
     * @param frame the inertial frame
     */
    protected OrbitalParameters(Frame frame) {
        cachedMu = Double.NaN;
        cachedPVCoordinates = new PVCoordinates(new Vector3D(Double.NaN, Double.NaN, Double.NaN),
                                                new Vector3D(Double.NaN, Double.NaN, Double.NaN));
        this.frame =  frame;
        dirtyCache = true;
    }

    /** Set the orbit from cartesian parameters.
     * @param pvCoordinates the position and velocity in the inertial frame
     * @param frame the frame in which are defined the {@link PVCoordinates}
     * @param mu central attraction coefficient (m^3/s^2)
     */
    protected OrbitalParameters(PVCoordinates pvCoordinates, Frame frame, double mu) {
        cachedMu = mu;
        cachedPVCoordinates = pvCoordinates;
        this.frame = frame;
        dirtyCache = false;
    }

    /** Get the semi-major axis.
     * @return semi-major axis (m)
     */
    public abstract double getA();

    /** Get the first component of the equinoctial eccentricity vector.
     * @return first component of the equinoctial eccentricity vector
     */
    public abstract double getEquinoctialEx();

    /** Get the second component of the equinoctial eccentricity vector.
     * @return second component of the equinoctial eccentricity vector
     */
    public abstract double getEquinoctialEy();

    /** Get the first component of the inclination vector.
     * @return first component of the inclination vector
     */
    public abstract double getHx();

    /** Get the second component of the inclination vector.
     * @return second component of the inclination vector
     */
    public abstract double getHy();

    /** Get the eccentric latitude argument.
     * @return eccentric latitude argument (rad)
     */
    public abstract double getLE();

    /** Get the true latitude argument.
     * @return true latitude argument (rad)
     */
    public abstract double getLv();

    /** Get the mean latitude argument.
     * @return mean latitude argument (rad)
     */
    public abstract double getLM();

    // Additional orbital elements

    /** Get the eccentricity.
     * @return eccentricity
     */
    public abstract double getE() ;

    /** Get the inclination.
     * @return inclination (rad)
     */
    public abstract double getI() ;

    private void initPVCoordinates(double mu) {

        // get equinoctial parameters
        final double a  = getA();
        final double ex = getEquinoctialEx();
        final double ey = getEquinoctialEy();
        final double hx = getHx();
        final double hy = getHy();
        final double lE = getLE();

        // inclination-related intermediate parameters
        final double hx2   = hx * hx;
        final double hy2   = hy * hy;
        final double factH = 1. / (1 + hx2 + hy2);

        // reference axes defining the orbital plane
        final double ux = (1 + hx2 - hy2) * factH;
        final double uy =  2 * hx * hy * factH;
        final double uz = -2 * hy * factH;

        final double vx = uy;
        final double vy = (1 - hx2 + hy2) * factH;
        final double vz =  2 * hx * factH;

        // eccentricity-related intermediate parameters
        final double exey = ex * ey;
        final double ex2  = ex * ex;
        final double ey2  = ey * ey;
        final double e2   = ex2 + ey2;
        final double eta  = 1 + Math.sqrt(1 - e2);
        final double beta = 1. / eta;

        // eccentric latitude argument
        final double cLe    = Math.cos(lE);
        final double sLe    = Math.sin(lE);
        final double exCeyS = ex * cLe + ey * sLe;

        // coordinates of position and velocity in the orbital plane
        final double x      = a * ((1 - beta * ey2) * cLe + beta * exey * sLe - ex);
        final double y      = a * ((1 - beta * ex2) * sLe + beta * exey * cLe - ey);

        final double factor = Math.sqrt(mu / a) / (1 - exCeyS);
        final double xdot   = factor * (-sLe + beta * ey * exCeyS);
        final double ydot   = factor * ( cLe - beta * ex * exCeyS);

        // cache the computed values
        cachedMu = mu;

        final Vector3D position =
            new Vector3D(x * ux + y * vx, x * uy + y * vy, x * uz + y * vz);
        final Vector3D velocity =
            new Vector3D(xdot * ux + ydot * vx, xdot * uy + ydot * vy, xdot * uz + ydot * vz);
        cachedPVCoordinates = new PVCoordinates(position, velocity);
        dirtyCache = false;

    }

    /** Check if cache is dirty.
     * @return true if cache is dirty
     */
    protected boolean cacheIsDirty() {
        return dirtyCache;
    }

    /** Get the cached central acceleration constant.
     * @return cached central acceleration constant
     */
    protected double getCachedMu() {
        return cachedMu;
    }

    /** Get the {@link PVCoordinates}.
     * Compute the position and velocity of the satellite. This method caches its
     * results, and recompute them only when the orbit is changed or if
     * the method is called with a new value for mu. The result is
     * provided as a reference to the internally cached {@link PVCoordinates}, so the
     * caller is responsible to copy it in a separate {@link PVCoordinates} if it needs
     * to keep the value for a while.
     * @param mu central attraction coefficient (m<sup>3</sup>/s<sup>2</sup>)
     * @return pvCoordinates in inertial frame (reference to an
     * internally cached pvCoordinates which can change)
     */
    public PVCoordinates getPVCoordinates(double mu) {
        if (dirtyCache || ((mu - cachedMu) != 0)) {
            initPVCoordinates(mu);
        }
        return cachedPVCoordinates;
    }

    /** Get the frame in which are defined the orbital parameters.
     * @return frame the frame
     */
    public Frame getFrame() {
        return frame;
    }

}