/* Copyright 2002-2012 CS Systèmes d'Information
 * Licensed to CS Systèmes d'Information (CS) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * CS licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.orekit.propagation.semianalytical.dsst.dsstforcemodel;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialsUtils;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.MathUtils;
import org.orekit.errors.OrekitException;
import org.orekit.errors.OrekitMessages;
import org.orekit.orbits.Orbit;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.semianalytical.dsst.coefficients.CiSiCoefficient;
import org.orekit.propagation.semianalytical.dsst.coefficients.DSSTCoefficientFactory;
import org.orekit.propagation.semianalytical.dsst.coefficients.DSSTCoefficientFactory.NSKey;
import org.orekit.propagation.semianalytical.dsst.coefficients.DSSTFactorial;
import org.orekit.propagation.semianalytical.dsst.coefficients.GHmsjPolynomials;
import org.orekit.propagation.semianalytical.dsst.coefficients.GammaMsnCoefficients;
import org.orekit.propagation.semianalytical.dsst.coefficients.HansenCoefficients;
import org.orekit.time.AbsoluteDate;

/**
 * Central body contribution to the {@link org.orekit.propagation.semianalytical.dsst.DSSTPropagator}.
 * <p>
 * Central body is divided into a mean contribution, which is integrated over long step period,
 * and some short periodic variations that can be analytically computed.
 * </p>
 * Mean element rate are the da<sub>i</sub>/dt derivatives.
 *
 * @author Romain Di Costanzo
 */
public class DSSTCentralBody extends AbstractGravitationalForces {

    /**
     * Truncation tolerance for analytically averaged central body spherical harmonics for orbits
     * which are always in vacuum.
     */
    private static final double    TRUNCATION_TOLERANCE_VACUUM = 1e-10;

    /**
     * Truncation tolerance for analytically averaged central body spherical harmonics for
     * drag-perturbed orbit.
     */
    private static final double    TRUNCATION_TOLERANCE_DRAG   = 1e-10;

    // Analytical central body spherical harmonic models
    /** Equatorial radius of the Central Body. */
    private final double           ae;

    /** Central body attraction coefficient (m<sup>3</sup>/s<sup>2</sup>). */
    private final double           mu;

    /** First normalized potential tesseral coefficients array. */
    private final double[][]       Cnm;

    /** Second normalized potential tesseral coefficients array. */
    private final double[][]       Snm;

    /** Degree <i>n</i> of non resonant C<sub>nm</sub> potential. */
    private final int              degree;

    /** Order <i>m</i> of non resonant C<sub>nm</sub> potential. */
    private final int              order;

    /** Maximum resonant order. */
    private int                    maxResonantOrder;

    /** Maximum resonant degree. */
    private int                    maxResonantDegree;

    /** List of resonant tesseral harmonic couple. */
    private List<ResonantCouple>   resonantTesseralHarmonic;

    /**
     * DSST model needs equinoctial orbit as internal representation. Classical equinoctial elements
     * have discontinuities when inclination is close to zero. In this representation, I = +1. <br>
     * To avoid this discontinuity, another representation exists and equinoctial elements can be
     * expressed in a different way, called "retrograde" orbit. This implies I = -1. As Orekit
     * doesn't implement the retrograde orbit, I = 1 here.
     */
    private int                    I                         = 1;

    /** current orbital state. */
    private Orbit                  orbit;

    /**
     * Equinoctial coefficients.
     */
    /** A = sqrt(&mu; * a). */
    private double                 A;

    /** B = sqrt(1 - ex<sup>2</sup> - ey<sup>2</sup>. */
    private double                 B;

    /** C = 1 + hx<sup>2</sup> + hx<sup>2</sup>. */
    private double                 C;

    // Direction cosines of the symmetry axis.

    /** &alpha. */
    private double                 alpha;

    /** &beta. */
    private double                 beta;

    /** &gamma. */
    private double                 gamma;

    // Internal variables.

    /** Coefficient used to define the mean disturbing function V<sub>ns</sub> coefficient. */
    private TreeMap<NSKey, Double> Vns;

    /**
     * Minimum period for analytically averaged high-order resonant central body spherical harmonics
     * in seconds. This value is set to 10 days, but can be overrides by using the
     * {@link #setResonantMinPeriodInSec(double)} method.
     */
    private double                 resonantMinPeriodInSec;

    /**
     * Minimum period for analytically averaged high-order resonant central body spherical harmonics
     * in satellite revolutions. This value is set to 10 satellite revolutions, but can be overrides
     * by using the {@link #setResonantMinPeriodInSatRev(double)} method.
     */
    private double                 resonantMinPeriodInSatRev;

    /** Geopotential coefficient Jn = -Cn0. */
    private double[]               Jn;

    /** Hansen coefficient. */
    private HansenCoefficients     hansen;

    /** &Gamma;<sub>n, s</sub> <sup>m</sup> (&gamma;) coefficient from equations 2.7.1 - (13). */
    private GammaMsnCoefficients   gammaMNS;

    /**
     * Highest power of the eccentricity to appear in the truncated analytical power series
     * expansion for the averaged central-body Zonal harmonic potential. The user can set this value
     * by using the {@link #setZonalMaximumEccentricityPower(double)} method. If he doesn't, the
     * software will compute a default value itself, through the
     * {@link #computeZonalMaxEccentricityPower()} method.
     */
    private int                    zonalMaxEccentricityPower;

    /**
     * Maximal value of the geopotential order that will be used in zonal series expansion. This
     * value won't be used if the {@link #zonalMaxEccentricityPower} is set through the
     * {@link #computeZonalMaxEccentricityPower()} method. If not, series expansion will
     * automatically be truncated.
     */
    private int                    zonalMaxDegree;

    /**
     * Highest power of the eccentricity to appear in the truncated analytical power series
     * expansion for the averaged central-body resonant Tesseral harmonic potential. The user can
     * set this value by using the {@link #setTesseralMaximumEccentricityPower(double)} method. If
     * he doesn't, the software will compute a default value itself, through the
     * {@link #computeTesseralMaxEccentricityPower()} method.
     */
    private int                    tesseralMaxEccentricityPower;

    /** Minimal integer value for the s index truncation in tesseral harmonic expansion. */
    private int                    tessMinS;

    /** Maximal integer value for the s index truncation in tesseral harmonic expansion. */
    private int                    tessMaxS;

    /** Minimal integer value for the N index truncation in tesseral harmonic expansion. */
    private int                    tessMaxN;

    /** Maximum power of the eccentricity in the Hansen coefficient kernels. */
    private int                    maximumHansen;

    /** Central-body rotation period in seconds. */
    private double                 omega;

    /**
     * Zonal truncation tolerance. This value is used by the
     * {@link DSSTCentralBody#zonalTruncation(SpacecraftState)} method which determines the upper
     * bound of the geopotential value.
     */
    private double                 zonalTruncationTolerance;

    /**
     * Tesseral trucation tolerance. This value is used by the
     * {@link DSSTCentralBody#tesseralTruncation(SpacecraftState)} method which determines the upper
     * bound for geopotential summation.
     */
    private double                 tesseralTruncationTolerance;


    /** DSST Central body constructor.
     * @param centralBodyRotationRate central body rotation rate in rad / s
     * @param ae Equatorial radius of the central body
     * @param mu &mu; of the central body
     * @param Cnm Cosines part of the spherical harmonics
     * @param Snm Sines part of the spherical harmonics
     * @param resonantTesseral
     *            Resonant Tesseral harmonic couple term. This parameter can be set to null or be an
     *            empty list. If so, the program will automatically determine the resonant couple to
     *            take in account. If not, only the resonant couple given by the user will be taken
     *            in account.
     */
    public DSSTCentralBody(final double centralBodyRotationRate, final double ae, final double mu,
                           final double[][] Cnm, final double[][] Snm,
                           final List<ResonantCouple> resonantTesseral) {

        // Get the central-body rotation period :
        this.omega = MathUtils.TWO_PI / centralBodyRotationRate;
        this.mu = mu;
        this.ae = ae;
        this.Cnm = Cnm;
        this.Snm = Snm;
        this.degree = Cnm.length - 1;
        this.order = Cnm[degree].length - 1;

        // Check potential coefficient consistency
        if ((Cnm.length != Snm.length) || (Cnm[Cnm.length - 1].length != Snm[Snm.length - 1].length)) {
            throw OrekitException.createIllegalArgumentException(OrekitMessages.POTENTIAL_ARRAYS_SIZES_MISMATCH, Cnm.length, Cnm[degree].length, Snm.length, Snm[degree].length);
        }

        // Initialize the Jn coefficient for zonal harmonic series expansion
        Jn = new double[degree + 1];
        for (int i = 0; i <= degree; i++) {
            Jn[i] = -Cnm[i][0];
        }

        // Store local variables
        if (resonantTesseral != null) {
            resonantTesseralHarmonic = resonantTesseral;
            if (resonantTesseralHarmonic.size() > 0) {
                // Get the maximal resonant order
                final ResonantCouple maxCouple = Collections.max(resonantTesseral);
                maxResonantOrder = maxCouple.getM();
                maxResonantDegree = maxCouple.getN();
            }
        } else {
            resonantTesseralHarmonic = new ArrayList<ResonantCouple>();
            // Set to a default undefined value
            maxResonantOrder = Integer.MIN_VALUE;
            maxResonantDegree = Integer.MIN_VALUE;
        }

        // Initialize default values
        this.resonantMinPeriodInSec = 864000d;
        this.resonantMinPeriodInSatRev = 10d;
        this.zonalMaxEccentricityPower = Integer.MIN_VALUE;
        this.tesseralMaxEccentricityPower = Integer.MIN_VALUE;
        this.maximumHansen = Integer.MIN_VALUE;
        this.zonalTruncationTolerance = Double.NEGATIVE_INFINITY;
        this.tesseralTruncationTolerance = Double.NEGATIVE_INFINITY;

    }

    /**
     * {@inheritDoc} From equation 3.1 - (1).
     */
    public final double[] getMeanElementRate(final SpacecraftState spacecraftState)
        throws OrekitException {

        // Store current state :
        orbit = spacecraftState.getOrbit();
        // Initialization of A, B, C, Alpha, Beta and Gamma coefficient :
        updateABCAlphaBetaGamma(orbit);
        // Initialize hansen coefficient
        hansen = new HansenCoefficients(orbit.getE(), maximumHansen);
        gammaMNS = new GammaMsnCoefficients(gamma, I);
        // Get zonal harmonics contribution :
        final ZonalHarmonics zonalHarmonics = new ZonalHarmonics();
        final double[] zonalTerms = zonalHarmonics.getZonalContribution(orbit);

        // Get tesseral resonant harmonics contribution :
        final TesseralResonantHarmonics tesseralHarmonics = new TesseralResonantHarmonics();
        final double[] tesseralTerms = tesseralHarmonics.getResonantContribution(orbit);

        final double[] meanElementRate = new double[zonalTerms.length];
        for (int i = 0; i < zonalTerms.length; i++) {
            meanElementRate[i] = tesseralTerms[i] + zonalTerms[i];
        }
        return meanElementRate;
    }

    /** {@inheritDoc} */
    public final double[] getShortPeriodicVariations(final AbsoluteDate date, final double[] meanElements)
        throws OrekitException {
        // TODO: not implemented yet : Short Periodic Variations are set to null
        return new double[] {
            0., 0., 0., 0., 0., 0.
        };
    }

    /** Update values used by the {@link DSSTCentralBody}.
     * @param o orbit from which values are computed
     */
    private void updateABCAlphaBetaGamma(final Orbit o) {
        // Factor declaration
        final double a = o.getA();
        final double k = o.getEquinoctialEx();
        final double h = o.getEquinoctialEy();
        final double k2 = k * k;
        final double h2 = h * h;
        final double q = o.getHx();
        final double p = o.getHy();
        final double q2 = q * q;
        final double p2 = p * p;

        A = FastMath.sqrt(mu * a);
        B = FastMath.sqrt(1 - k2 - h2);
        C = 1 + q2 + p2;

        // Direction cosines :
        final Vector3D[] equinoctialFrame = computeEquinoctialReferenceFrame(o);
        alpha = equinoctialFrame[0].getZ();
        beta = equinoctialFrame[1].getZ();
        gamma = equinoctialFrame[2].getZ();
    }

    /**
     * Compute the equinoctioal reference frame defined by the (f, g, w) vector. f and g lie in the
     * satellite orbit plane. w is parallel to the angular momentum vector of the satellite.
     * @param o orbit
     * @return the equinoctial reference frame
     */
    private Vector3D[] computeEquinoctialReferenceFrame(final Orbit o) {
        // Factor declaration
        final double q = o.getHx();
        final double p = o.getHy();
        final double q2 = q * q;
        final double p2 = p * p;

        final double num = 1. / (1 + q2 + p2);

        // compute the f vector :
        final double fx = 1 - p2 + q2;
        final double fy = 2 * p * q;
        final double fz = -2 * I * p;
        final Vector3D f = new Vector3D(num, new Vector3D(fx, fy, fz));

        // Compute the g vector :
        final double gx = 2 * I * p * q;
        final double gy = (1 + p2 - q2) * I;
        final double gz = 2 * q;
        final Vector3D g = new Vector3D(num, new Vector3D(gx, gy, gz));

        // Compute the w vector :
        final Vector3D w = Vector3D.crossProduct(f, g);
        return new Vector3D[] {
            f, g, w
        };
    }

    /**
     * Compute the &theta; angle for the current orbit. The &theta; angle is the central body
     * rotation angle, defined from the equinoctial reference frame. See equation 2.7.1 - (3)(4)
     *
     * @param o
     *            current orbital state
     * @return the central body rotation angle
     */
    private double computeThetaAngle(final Orbit o) {
        final Vector3D[] frame = computeEquinoctialReferenceFrame(o);
        final Vector3D f = frame[0];
        final Vector3D g = frame[1];

        final double num = -f.getY() + I * g.getX();
        final double den = f.getX() + I * g.getY();

        return FastMath.atan2(num, den);
    }

    /**
     * {@inheritDoc} This method here will find the resonant tesseral terms in the central body
     * harmonic field. The routine computes the repetition period of the perturbation caused by each
     * central-body sectoral and tesseral harmonic term and compares the period to a predetermined
     * tolerance, the minimum period considered to be resonant.
     *
     * @throws OrekitException
     */
    public final void initialize(final SpacecraftState initialState) throws OrekitException {
        updateABCAlphaBetaGamma(initialState.getOrbit());

        /**
         * Resonant Tesseral parameterization and truncation :
         */
        // Compute the central body resonant tesseral harmonic terms
        computeResonantTesseral(initialState);

        // Set the highest power of the eccentricity in the analytical power series expansion for
        // the averaged high order resonant central body spherical harmonic perturbation
        computeResonantTesseralMaxEccPower(initialState);

        // TODO Not sure about this part :
        // Truncation of the central body tesseral harmonic :
        if (resonantTesseralHarmonic.size() > 0) {
            // tesseralTruncation(initialState);
            tessMaxN = Collections.max(resonantTesseralHarmonic).getN();
        } else {
            tessMaxN = degree;
        }
        tessMinS = tesseralMaxEccentricityPower;
        tessMaxS = tesseralMaxEccentricityPower;

        // Get the maximum power of E to use in Hansen coefficient Kernel expansion
        computeHansenMaximumEccentricity(initialState);

        /**
         * Zonal parameterization and truncation :
         */
        // Set the highest power of the eccentricity in the analytical power series expansion for
        // the averaged low order central body spherical harmonic perturbation
        zonalTruncation(initialState);

        Vns = DSSTCoefficientFactory.computeVnsCoefficient(zonalMaxDegree + 1);
    }

    /**
     * The expansion of the central body tesseral harmonics has four truncatable indices. The
     * algorithm is to determine the maximum value of each of those indices by computing the upper
     * |R<sub>jmsn</sub>| perturbation function value for every indices. <br>
     * Algorithm description can be found in the D.A Danielson paper at paragraph 6.3
     *
     * @param initialState
     *            Initial satellite State
     * @throws OrekitException
     *             if an error occurs when computing Hansen upper bound
     */
    private void tesseralTruncation(final SpacecraftState initialState) throws OrekitException {

        // Check if a value has been entered by the user :
        if (tesseralTruncationTolerance == Double.NEGATIVE_INFINITY) {
            tesseralTruncationTolerance = TRUNCATION_TOLERANCE_VACUUM;
        }

        // Temporary variables :
        int sMin = Integer.MAX_VALUE;
        int sMax = Integer.MIN_VALUE;
        int n;
        final double e = initialState.getE();
        final double a = initialState.getA();
        boolean sLoop = true;

        // J-loop j = 0, +-1, +-2 ...
        // Resonant term identified :
        final Iterator<ResonantCouple> iterator = resonantTesseralHarmonic.iterator();
        // Iterative process :

        while (iterator.hasNext()) {
            final ResonantCouple resonantTesseralCouple = iterator.next();
            final int j = resonantTesseralCouple.getN();
            int m = resonantTesseralCouple.getM();
            int sbis = 0;
            // S-loop : s = j, j+-1, j+-2 ...
            int s = j;
            while (sLoop) {
                final int signS = (int) FastMath.pow(-1, s);
                sbis += s * signS;
                sMin = FastMath.min(sMin, sbis);
                sMax = FastMath.max(sMax, sbis);

                // N-loop : n = Max(2, m, |s|), n-m even and n < N. N being the maximum
                // potential degree
                n = FastMath.max(FastMath.max(2, m), FastMath.abs(sbis));

                if (n > degree) {
                    break;
                }

                if ((n - sbis) % 2 == 0) {

                    // Compute the perturbation function upper bound :
                    final double hansenUp = HansenCoefficients.computeUpperBound(e, j, -n - 1, sbis);

                    // Compute Jacobi polynomials upper bound :
                    final int l = (sbis <= m) ? (n - m) : n - sbis;
                    final int v = FastMath.abs(m - sbis);
                    final int w = FastMath.abs(m + sbis);

                    final PolynomialFunction jacobi = PolynomialsUtils.createJacobiPolynomial(l, v, w);
                    final double jacDer = jacobi.derivative().value(gamma);
                    final double jacDer2 = jacDer * jacDer;
                    final double jacGam = jacobi.value(gamma);
                    final double jacGam2 = jacGam * jacGam;
                    final double jacFact = (1 - gamma * gamma) / (l * (v + w + l + 1));
                    final double jacobiUp = FastMath.sqrt(jacGam2 + jacFact * jacDer2);

                    // Upper bound for |Cnm - iSnm|
                    final double cnm = Cnm[n][m];
                    final double cnm2 = cnm * cnm;
                    final double snm = Snm[n][m];
                    final double snm2 = snm * snm;
                    final double csnmUp = FastMath.sqrt(cnm2 + snm2);

                    // Upper bound for the |Gmsj + iHmsj|
                    final double maxE = FastMath.pow(e, FastMath.abs(sbis - j));
                    final int p = FastMath.abs(sbis - I * m) / 2;
                    final double maxG = FastMath.pow(1 - gamma * gamma, p);
                    final double ghmsUp = maxE * maxG;

                    // Upper bound for Vmns
                    final double vmnsUp = FastMath.abs(DSSTCoefficientFactory.getVmns(m, n, sbis));
                    // Upper bound for Gammamsn
                    final GammaMsnCoefficients gmns = new GammaMsnCoefficients(gamma, I);
                    final double gmnsUp = FastMath.abs(gmns.getGammaMsn(n, sbis, m));

                    // Upper perturbation function value
                    final double common = (mu / a) * FastMath.pow(ae / a, n);
                    final double upperValue = common * vmnsUp * gmnsUp * hansenUp * jacobiUp * csnmUp * ghmsUp;

                    if (upperValue <= tesseralTruncationTolerance) {
                        // Store values :
                        tessMinS = FastMath.abs(sMin);
                        tessMaxS = sMax;
                        tessMaxN = n;

                        // Force loop to stop :
                        sLoop = false;
                        m = order;
                        n = degree;
                    }
                }
                s++;
            }
        }
    }

    /** Compute the maximum power of the eccentricity to use in Hansen coefficient Kernel expansion.
     * @param initialState initial satellite state
     */
    private void computeHansenMaximumEccentricity(final SpacecraftState initialState) {
        if (maximumHansen != Integer.MIN_VALUE) {
            // Set the maximum value to tesseralMaxEccentricityPower / 2
            maximumHansen = Math.min(maximumHansen, 10);
        } else {
            maximumHansen = (int) tesseralMaxEccentricityPower / 2;
        }
    }

    /**
     * Computes the highest power of the eccentricity to appear in the truncated
     * analytical power series expansion for the averaged central-body resonant tesseral harmonic
     * potential.
     * Analytical averaging should not be used for resonant harmonics if the eccentricity is greater
     * than 0.5.
     *
     * @param initialState initial satellite state
     * @throws OrekitException if eccentricity is > 0.5
     */
    private void computeResonantTesseralMaxEccPower(final SpacecraftState initialState) throws OrekitException {
        // Is the maximum d'Alenbert characteristic given by the user ?
        if (tesseralMaxEccentricityPower != Integer.MIN_VALUE) {
            // Set the maximum possible power expansion to 20
            tesseralMaxEccentricityPower = Math.min(tesseralMaxEccentricityPower, 20);
        } else {
            // Automatically select the maximum d'Alembert characteristic
            final double ecc = initialState.getE();
            // Set the correct d'Alembert characteristic from the satellite eccentricity
            if (ecc < 5E-3) {
                tesseralMaxEccentricityPower = 3;
            } else if (ecc <= 0.02) {
                tesseralMaxEccentricityPower = 4;
            } else if (ecc <= 0.1) {
                tesseralMaxEccentricityPower = 7;
            } else if (ecc <= 0.2) {
                tesseralMaxEccentricityPower = 10;
            } else if (ecc <= 0.3) {
                tesseralMaxEccentricityPower = 12;
            } else if (ecc <= 0.4) {
                tesseralMaxEccentricityPower = 15;
            } else if (ecc <= 0.5) {
                tesseralMaxEccentricityPower = 20;
            } else {
                throw new OrekitException(OrekitMessages.DSST_ECC_NO_NUMERICAL_AVERAGING_METHOD, ecc);
            }
        }
    }

    /**
     * This subroutine computes the highest power of the eccentricity and the maximal zonal degree
     * to appear in the truncated analytical power series expansion for the averaged central-body
     * zonal harmonic potential. <br>
     * This method is computing the upper value for the central body geopotential and then determine
     * the maximal values from with upper values gives geopotential terms inferior to a defined
     * tolerance. <br>
     * Algorithm description can be found in the D.A Danielson paper at paragraph 6.2
     *
     * @param initialState initial satellite state
     * @throws OrekitException if an error occurs in Hansen coefficient computation
     */
    private void zonalTruncation(final SpacecraftState initialState) throws OrekitException {
        // Did a maximum eccentricity power has been found
        boolean maxFound = false;
        // Initialize the current spherical harmonic term to 0.
        double term = 0.;
        // Maximal degree of the geopotential expansion :
        int nMax = Integer.MIN_VALUE;
        // Maximal power of e
        int sMax = Integer.MIN_VALUE;
        // Find the truncation tolerance : set tolerance as a non dragged satellite if undefined by
        // the user. Operation stops when term > tolerance
        if (zonalTruncationTolerance == Double.NEGATIVE_INFINITY) {
            zonalTruncationTolerance = TRUNCATION_TOLERANCE_VACUUM;
        }
        // Check if highest power of E has been given by the user :
        if (zonalMaxEccentricityPower == Integer.MIN_VALUE) {
            // Is the degree of the zonal harmonic field too small to allow more than one power of E
            if (degree == 2) {
                zonalMaxEccentricityPower = 0;
                zonalMaxDegree = degree;
            } else {
                // Auxiliary quantities
                final double ecc = initialState.getE();
                final double r2a = ae / (2 * initialState.getA());
                double x2MuRaN = 2 * mu / (initialState.getA()) * r2a;
                // Search for the highest power of E for which the computed value is greater than
                // the truncation tolerance in the power series
                // s-loop :
                for (int s = 0; s <= degree - 2; s++) {
                    // n-loop
                    for (int n = s + 2; n <= degree; n++) {
                        // (n - s) must be even
                        if ((n - s) % 2 == 0) {
                            // Local values :
                            final double gam2 = gamma * gamma;
                            // Compute factorial :
                            final BigInteger factorialNum = DSSTFactorial.fact(n - s);
                            final BigInteger factorialDen = DSSTFactorial.fact((n + s) / 2).multiply(DSSTFactorial.fact((n - s) / 2));
                            final double factorial = factorialNum.doubleValue() / factorialDen.doubleValue();
                            final double k0 = new HansenCoefficients(ecc).getHansenKernelValue(0, -n - 1, s);
                            // Compute the Qns(bound) upper bound :
                            final double qns = FastMath.abs(DSSTCoefficientFactory.getQnsPolynomialValue(gamma, n, s));
                            final double qns2 = qns * qns;
                            final double factor = (1 - gam2) / (n * (n + 1) - s * (s + 1));
                            // Compute dQns/dGamma
                            final double dQns = FastMath.abs(DSSTCoefficientFactory.getQnsPolynomialValue(gamma, n, s + 1));
                            final double dQns2 = dQns * dQns;
                            final double qnsBound = FastMath.sqrt(qns2 + factor * dQns2);

                            // Get the current potential upper bound for the current (n, s) couple.
                            final int sO2 = s / 2;
                            term = x2MuRaN * r2a * FastMath.abs(Jn[n]) * factorial * k0 * qnsBound *
                                   FastMath.pow(1 - gam2, sO2) * FastMath.pow(ecc, s) / FastMath.pow(2, n);

                            // Compare result with the tolerance parameter :
                            if (term <= zonalTruncationTolerance) {
                                // Stop here
                                nMax = Math.max(nMax, n);
                                sMax = Math.max(sMax, s);
                                // truncature found
                                maxFound = true;
                                // Force a premature end loop
                                n = degree;
                                s = degree;
                            }
                        }
                    }
                    // Prepare next loop :
                    x2MuRaN = 2 * mu / (initialState.getA()) * FastMath.pow(r2a, s + 1);
                }
                if (maxFound) {
                    zonalMaxDegree = nMax;
                    zonalMaxEccentricityPower = sMax;
                } else {
                    zonalMaxDegree = degree;
                    zonalMaxEccentricityPower = degree - 2;
                }
            }

        } else {
            // Value set by the user :
            zonalMaxDegree = degree;
            zonalMaxEccentricityPower = degree - 2;
        }

    }

    /**
     * This subroutine finds the resonant tesseral terms in the central body spherical harmonic
     * field. The routine computes the repetition period of the perturbation caused by each central
     * body sectoral and tesseral harmonic term and compares the period to a predetermined
     * tolerance, the minimum period considered to be resonant.
     *
     * @param initialState
     *            Initial satellite state
     */
    private void computeResonantTesseral(final SpacecraftState initialState) {
        // Get the satellite period
        final double satellitePeriod = initialState.getKeplerianPeriod();
        // Compute ration of satellite period to central body rotation period
        final double ratio = satellitePeriod / omega;

        // If user didn't define a maximum resonant order, use the maximum central-body's
        // order
        if (maxResonantOrder == Integer.MIN_VALUE) {
            maxResonantOrder = order;
        }

        if (maxResonantDegree == Integer.MIN_VALUE) {
            maxResonantDegree = degree;
        }

        // Has the user requested a specific set of resonant tesseral harmonic terms ?
        if (resonantTesseralHarmonic.size() == 0) {

            final int maxResonantOrderTmp = maxResonantOrder;
            // Reinitialize the maxResonantOrder parameter :
            maxResonantOrder = 0;

            double tolerance = resonantMinPeriodInSec / satellitePeriod;
            if (tolerance < resonantMinPeriodInSatRev) {
                tolerance = resonantMinPeriodInSatRev;
            }
            tolerance = 1d / tolerance;

            // Now find the order of the resonant tesseral harmonic field
            for (int m = 1; m <= maxResonantOrderTmp; m++) {
                final double resonance = ratio * m;
                final int j = (int) FastMath.ceil(resonance);
                if (FastMath.abs(resonance - j) <= tolerance && j > 0d) {
                    // Update the maximum resonant order found
                    maxResonantOrder = m;

                    // Store each resonant couple for a given order
                    int n = m;
                    // insert resonant terms into the resonant field until either 10 terms or all of
                    // the resonant terms are chosen
                    while (n <= degree && resonantTesseralHarmonic.size() < 10) {
                        resonantTesseralHarmonic.add(new ResonantCouple(n, m));
                        n++;
                    }
                }
            }
        }
    }

    /**
     * Set the minimum period for analytically averaged high-order resonant central body spherical
     * harmonics in seconds. Set to 10 days by default.
     *
     * @param resonantMinPeriodInSec
     *            minimum period in seconds
     */
    public final void setResonantMinPeriodInSec(final double resonantMinPeriodInSec) {
        this.resonantMinPeriodInSec = resonantMinPeriodInSec;
    }

    /**
     * Set the minimum period for analytically averaged high-order resonant central body spherical
     * harmonics in satelliteRevolution. Set to 10 by default.
     *
     * @param resonantMinPeriodInSatRev
     *            minimum period in satellite revolution
     */
    public final void setResonantMinPeriodInSatRev(final double resonantMinPeriodInSatRev) {
        this.resonantMinPeriodInSatRev = resonantMinPeriodInSatRev;
    }

    /**
     * This methode set the highest power of the eccentricity to appear in the truncated analytical
     * power series expansion for the averaged central-body zonal harmonic potential.
     *
     * @param zonalMaxEccPower
     *            highest power of the eccentricity
     */
    public final void setZonalMaximumEccentricityPower(final int zonalMaxEccPower) {
        this.zonalMaxEccentricityPower = zonalMaxEccPower;
    }

    /** Set the Zonal truncature tolerance.
     * @param zonalTruncatureTolerance Zonal truncature tolerance
     */
    public final void setZonalTruncatureTolerance(final double zonalTruncatureTolerance) {
        this.zonalTruncationTolerance = zonalTruncatureTolerance;
    }

    /**
     * Set the highest power of the eccentricity to appear in the truncated analytical
     * power series expansion for the averaged central-body tesseral harmonic potential.
     *
     * @param tesseralMaxEccPower highest power of the eccentricity
     */
    public final void setTesseralMaximumEccentricityPower(final int tesseralMaxEccPower) {
        this.tesseralMaxEccentricityPower = tesseralMaxEccPower;
    }

    /** Get the equatorial radius.
     * @return a<sub>e</sub>
     */
    public final double getAe() {
        return ae;
    }

    /** Get the first normalized potential tesseral coefficients array.
     * @return First normalized potential tesseral coefficients array
     */
    public final double[][] getCnm() {
        return Cnm;
    }

    /** Get the second normalized potential tesseral coefficients array.
     * @return Second normalized potential tesseral coefficients array
     */
    public final double[][] getSnm() {
        return Snm;
    }

    /** Zonal contribution of the central body for the first order mean element rates.
     */
    private final class ZonalHarmonics {

        /**
         * Dummy constructor.
         */
        private ZonalHarmonics() {
            // Dummy constructor, nothing to do.
        }

        /** Get zonal contribution.
         * @param o orbit
         * @return orbital elements variation rate
         * @throws OrekitException if Hansen coefficients cannot be computed
         */
        private double[] getZonalContribution(final Orbit o) throws OrekitException {
            // Initialization :
            double dh = 0d;
            double dk = 0d;
            double dp = 0d;
            double dq = 0d;
            double dM = 0d;

            final double a = o.getA();
            final double k = o.getEquinoctialEx();
            final double h = o.getEquinoctialEy();
            final double q = o.getHx();
            final double p = o.getHy();

            final double[][] GsHs = DSSTCoefficientFactory.computeGsHsCoefficient(k, h, alpha, beta, zonalMaxDegree + 1);

            final double[][] Qns = DSSTCoefficientFactory.computeQnsCoefficient(gamma, zonalMaxDegree + 1);

            // Compute potential derivative :
            final double[] potentialDerivatives = computePotentialderivatives(Qns, GsHs);

            final double dUda = potentialDerivatives[0];
            final double dUdk = potentialDerivatives[1];
            final double dUdh = potentialDerivatives[2];
            final double dUdAl = potentialDerivatives[3];
            final double dUdBe = potentialDerivatives[4];
            final double dUdGa = potentialDerivatives[5];

            // Compute cross derivatives from formula 2.2 - (8):
            // U(alpha,gamma) = alpha * du / dgamma - gamma * du / dalpha
            final double UAlphaGamma = alpha * dUdGa - gamma * dUdAl;
            // U(beta,gamma) = beta * du / dgamma - gamma * du / dbeta
            final double UBetaGamma = beta * dUdGa - gamma * dUdBe;

            final double factor = (p * UAlphaGamma - I * q * UBetaGamma) / (A * B);

            // Compute mean element Rate for Zonal Harmonic :
            // da / dt = 0 for zonal harmonic :
            dh = (B / A) * dUdk + k * factor;
            dk = -(B / A) * dUdh - h * factor;
            dp = -C / (2 * A * B) * UBetaGamma;
            dq = -I * C * UAlphaGamma / (2 * A * B);
            dM = (-2. * a * dUda / A) + (B / (A * (1 + B))) * (h * dUdh + k * dUdk) +
                 (p * UAlphaGamma - I * q * UBetaGamma) / (A * B);

            return new double[] {
                0d, dk, dh, dq, dp, dM
            };
        }

        /**
         * Get the potential derivatives needed for the central body gravitational zonal harmonics
         * at first order. As those equations depend on the &alpha; &beta; and &gamma; values, they
         * only can be evaluated after those data have been computed (from the current state). See
         * equation 3.1 - (6) from the main paper. <br>
         * The result is an array containing the following data : <br>
         * dU / da <br>
         * dU / dk <br>
         * dU / dh <br>
         * dU / d&alpha; <br>
         * dU/ d&beta; <br>
         * dU/d&gamma;<br>
         * Where U is the gravitational potential.
         *
         * @param Qns Qns array
         * @param GsHs GsHs array
         * @return data needed for the potential derivatives
         * @throws OrekitException if an error occurs in hansen computation
         */
        private double[] computePotentialderivatives(final double[][] Qns, final double[][] GsHs)
            throws OrekitException {

            // Initialize data
            final double a = orbit.getA();
            final double k = orbit.getEquinoctialEx();
            final double h = orbit.getEquinoctialEy();

            // mu / a
            final double factor = mu / a;
            final double Ra = ae / a;
            final double khi = 1 / B;

            // Potential derivatives
            double dUda = 0d;
            double dUdk = 0d;
            double dUdh = 0d;
            double dUdAl = 0d;
            double dUdBe = 0d;
            double dUdGa = 0d;

            // dGs / dk
            double dGsdk = 0d;
            // dGs / dh
            double dGsdh = 0d;
            // dGs / dAlpha
            double dGsdAl = 0d;
            // dGs / dBeta
            double dGsdBe = 0d;

            // series coefficient :
            double jn;
            double vns;
            double kns;
            double qns;
            double gs;
            double gsM1;
            double hsM1;
            double dkns;

            // Other data :
            // (R / a)^n
            final double khi3 = khi * khi * khi;
            // Kronecker symbol (2 - delta(0,s))

            for (int s = 0; s <= zonalMaxEccentricityPower; s++) {
                // Get the current gs and hs coefficient :
                gs = GsHs[0][s];

                // Compute partial derivatives of Gs from equ. (9) :
                // First get the G(s-1) and the H(s-1) coefficient : SET TO 0 IF < 0
                gsM1 = s > 0 ? GsHs[0][s - 1] : 0;
                hsM1 = s > 0 ? GsHs[1][s - 1] : 0;
                // Get derivatives
                dGsdh = s * beta * gsM1 - s * alpha * hsM1;
                dGsdk = s * alpha * gsM1 + s * beta * hsM1;
                dGsdAl = s * k * gsM1 - s * h * hsM1;
                dGsdBe = s * h * gsM1 + s * k * hsM1;

                // get (2 - delta0s)
                final double delta0s = (s == 0) ? 1 : 2;

                for (int n = s + 2; n <= zonalMaxDegree; n++) {
                    // Extract data from previous computation :
                    jn = Jn[n];
                    vns = Vns.get(new NSKey(n, s));
                    kns = hansen.getHansenKernelValue(0, -n - 1, s);
                    qns = Qns[n][s];
                    final double raExpN = FastMath.pow(Ra, n);
                    dkns = hansen.getHansenKernelDerivative(0, -n - 1, s);
                    final double commonCoefficient = delta0s * raExpN * jn * vns;

                    // Compute dU / da :
                    dUda += commonCoefficient * (n + 1) * kns * qns * gs;
                    // Compute dU / dEx
                    dUdk += commonCoefficient * qns * (kns * dGsdk + k * khi3 * gs * dkns);
                    // Compute dU / dEy
                    dUdh += commonCoefficient * qns * (kns * dGsdh + h * khi3 * gs * dkns);
                    // Compute dU / dAlpha
                    dUdAl += commonCoefficient * qns * kns * dGsdAl;
                    // Compute dU / dBeta
                    dUdBe += commonCoefficient * qns * kns * dGsdBe;
                    // Compute dU / dGamma : here dQns/dGamma = Q(n, s + 1) from Equation 3.1 - (8)
                    dUdGa += commonCoefficient * kns * Qns[n][s + 1] * gs;
                }
            }

            dUda *= factor / a;
            dUdk *= -factor;
            dUdh *= -factor;
            dUdAl *= -factor;
            dUdBe *= -factor;
            dUdGa *= -factor;

            return new double[] {
                dUda, dUdk, dUdh, dUdAl, dUdBe, dUdGa
            };
        }
    }

    /** Central-Body Gravitational Resonant Tesserals Harmonics for the first order contribution. */
    private final class TesseralResonantHarmonics {

        /** Dummy constructor. */
        private TesseralResonantHarmonics() {
            // Dummy constructor, nothing to do.
        }

        /** Get tesseral contribution.
         * @param o orbit
         * @return orbital elements variation rate
         * @throws OrekitException if an error occurs in Hansen computation
         */
        private double[] getResonantContribution(final Orbit o) throws OrekitException {
            // Get orbital parameters :
            final double a = o.getA();
            final double k = o.getEquinoctialEx();
            final double h = o.getEquinoctialEy();
            final double q = o.getHx();
            final double p = o.getHy();

            // Compute potential derivatives
            final double[] dU = computePotentialDerivatives(o);
            final double duda = dU[0];
            final double dudh = dU[1];
            final double dudk = dU[2];
            final double dudl = dU[3];
            final double dudal = dU[4];
            final double dudbe = dU[5];
            final double dudga = dU[6];

            // Compute the cross derivative operator :
            final double UAlphaGamma = alpha * dudga - gamma * dudal;
            final double UAlphaBeta = alpha * dudbe - beta * dudal;
            final double UBetaGamma = beta * dudga - gamma * dudbe;
            final double Uhk = h * dudk - k * dudh;

            final double aDot = 2 * a / A * dudal;
            final double hDot = B * dudk / A + k / (A * B) * (p * UAlphaGamma - I * q * UBetaGamma) - h * B * dudl / (A * (1 + B));
            final double kDot = -(B * dudh / A + h / (A * B) * (p * UAlphaGamma - I * q * UBetaGamma) + k * B * dudl / (A * (1 + B)));
            final double pDot = C / (2 * A * B) * (p * (Uhk - UAlphaBeta - dudl) - UBetaGamma);
            final double qDot = C / (2 * A * B) * (p * (Uhk - UAlphaBeta - dudl) - I * UAlphaGamma);
            final double lDot = -2 * a * duda / A + B / (A * (1 + B)) * (h * dudh + k * dudk) +
                                (p * UAlphaGamma - I * q * UBetaGamma) / (A * B);

            return new double[] {
                aDot, hDot, kDot, pDot, qDot, lDot
            };

        }

        /**
         * Compute the following elements from expression 3.3 - (4). If tesseral harmonic have been
         * identified (automatically or set by user), they are the only one to be taken in account.
         * If no resonant term have been found, we compute non resonant tessral term from those
         * found by the {@link DSSTCentralBody#tesseralTruncation(SpacecraftState)} method.
         *
         * <pre>
         * dU / da
         * dU / dh
         * dU / dk
         * dU / d&lambda;
         * dU / d&alpha;
         * dU / d&beta;
         * dU / d&gamma;
         *
         * </pre>
         *
         * @param o initial orbit
         * @return potential derivatives
         * @throws OrekitException
         *             if an error occurs in Hansen computation
         */
        private double[] computePotentialDerivatives(final Orbit o) throws OrekitException {
            // Result initialization
            double duda = 0d;
            double dudh = 0d;
            double dudk = 0d;
            double dudl = 0d;
            double dudal = 0d;
            double dudbe = 0d;
            double dudga = 0d;

            final double a = o.getA();
            final double muOa = mu / a;

            // Resonant term identified :
            final Iterator<ResonantCouple> iterator = resonantTesseralHarmonic.iterator();
            // Iterative process :

            while (iterator.hasNext()) {
                final ResonantCouple resonantTesseralCouple = iterator.next();
                final int j = resonantTesseralCouple.getN();
                final int m = resonantTesseralCouple.getM();

                final double[] potential = tesseralPotentialComputation(j, m);
                duda += potential[0];
                dudh += potential[1];
                dudk += potential[2];
                dudl += potential[3];
                dudal += potential[4];
                dudbe += potential[5];
                dudga += potential[6];
            }

            duda *= -muOa / a;
            dudh *= muOa;
            dudk *= muOa;
            dudl *= muOa;
            dudal *= muOa;
            dudbe *= muOa;
            dudga *= muOa;

            return new double[] {
                duda, dudh, dudk, dudl, dudal, dudbe, dudga
            };
        }

        /** Compute potential for tesseral harmonic terms.
         * @param j j-index
         * @param m m-index
         * @return potential derivatives
         * @throws OrekitException if an error occurs in Hansen computation
         */
        private double[] tesseralPotentialComputation(final int j, final int m)
            throws OrekitException {

            // Get needed orbital elements
            final double a = orbit.getA();
            final double k = orbit.getEquinoctialEx();
            final double h = orbit.getEquinoctialEy();

            // Result initialization
            double duda = 0d;
            double dudh = 0d;
            double dudk = 0d;
            double dudl = 0d;
            double dudal = 0d;
            double dudbe = 0d;
            double dudga = 0d;

            final double ra = ae / a;
            final double theta = computeThetaAngle(orbit);
            final double lambda = orbit.getLM();
            final CiSiCoefficient cisiKH = new CiSiCoefficient(k, h);
            final CiSiCoefficient cisiAB = new CiSiCoefficient(alpha, beta);
            final GHmsjPolynomials GHms = new GHmsjPolynomials(cisiKH, cisiAB, I);

            // Jacobi indices
            final double jlMmt = j * lambda - m * theta;
            final double sinPhi = FastMath.sin(jlMmt);
            final double cosPhi = FastMath.cos(jlMmt);

            final int Im = (int) FastMath.pow(I, m);
            // Sum(-N, N)
            for (int s = -tessMinS; s <= tessMaxS; s++) {
                // Sum(Max(2, m, |s|))
                final int nmin = Math.max(Math.max(2, m), Math.abs(s));

                // jacobi v, w, indices : see 2.7.1 - (15)
                final int v = FastMath.abs(m - s);
                final int w = FastMath.abs(m + s);
                for (int n = nmin; n <= tessMaxN; n++) {
                    // (R / a)^n
                    final double ran = FastMath.pow(ra, n);
                    final double vmsn = DSSTCoefficientFactory.getVmns(m, n, s);
                    final double gamMsn = gammaMNS.getGammaMsn(n, s, m);
                    final double dGamma = gammaMNS.getDGammaMsn(n, s, m);
                    final double kjn_1 = hansen.getHansenKernelValue(j, -n - 1, s);
                    // kjn_1 = hansen.computHKVfromNewcomb(j, -n - 1, s);
                    final double dkjn_1 = hansen.getHansenKernelDerivative(j, -n - 1, s);
                    final double dGdh = GHms.getdGmsdh(m, s, j);
                    final double dGdk = GHms.getdGmsdk(m, s, j);
                    final double dGdA = GHms.getdGmsdAlpha(m, s, j);
                    final double dGdB = GHms.getdGmsdBeta(m, s, j);
                    final double dHdh = GHms.getdHmsdh(m, s, j);
                    final double dHdk = GHms.getdHmsdk(m, s, j);
                    final double dHdA = GHms.getdHmsdAlpha(m, s, j);
                    final double dHdB = GHms.getdHmsdBeta(m, s, j);

                    // Jacobi l-indices : see 2.7.1 - (15)
                    final int l = FastMath.abs(s) <= m ? (n - m) : n - FastMath.abs(s);
                    final PolynomialFunction jacobiPoly = PolynomialsUtils.createJacobiPolynomial(l, v, w);
                    final double jacobi = jacobiPoly.value(gamma);
                    final double dJacobi = jacobiPoly.derivative().value(gamma);
                    final double gms = GHms.getGmsj(m, s, j);
                    final double hms = GHms.getHmsj(m, s, j);
                    final double cnm = Cnm[n][m];
                    final double snm = Snm[n][m];

                    // Compute dU / da from expansion of equation (4-a)
                    double realCosFactor = (gms * cnm + hms * snm) * cosPhi;
                    double realSinFactor = (gms * snm - hms * cnm) * sinPhi;
                    duda += (n + 1) * ran * Im * vmsn * gamMsn * kjn_1 * jacobi * (realCosFactor + realSinFactor);

                    // Compute dU / dh from expansion of equation (4-b)
                    realCosFactor = (cnm * kjn_1 * dGdh + 2 * cnm * h * (gms + hms) * dkjn_1 + snm * kjn_1 * dHdh) * cosPhi;
                    realSinFactor = (-cnm * kjn_1 * dHdh + 2 * snm * h * (gms + hms) * dkjn_1 + snm * kjn_1 * dGdh) * sinPhi;
                    dudh += ran * Im * vmsn * gamMsn * jacobi * (realCosFactor + realSinFactor);

                    // Compute dU / dk from expansion of equation (4-c)
                    realCosFactor = (cnm * kjn_1 * dGdk + 2 * cnm * k * (gms + hms) * dkjn_1 + snm * kjn_1 * dHdk) * cosPhi;
                    realSinFactor = (-cnm * kjn_1 * dHdk + 2 * snm * k * (gms + hms) * dkjn_1 + snm * kjn_1 * dGdk) * sinPhi;
                    dudk += ran * Im * vmsn * gamMsn * jacobi * (realCosFactor + realSinFactor);

                    // Compute dU / dLambda from expansion of equation (4-d)
                    realCosFactor = (snm * gms - hms * cnm) * cosPhi;
                    realSinFactor = (snm * hms + gms * cnm) * sinPhi;
                    dudl += j * ran * Im * vmsn * kjn_1 * jacobi * (realCosFactor - realSinFactor);

                    // Compute dU / alpha from expansion of equation (4-e)
                    realCosFactor = (dGdA * cnm + dHdA * snm) * cosPhi;
                    realSinFactor = (dGdA * snm - dHdA * cnm) * sinPhi;
                    dudal += ran * Im * vmsn * gamMsn * kjn_1 * jacobi * (realCosFactor + realSinFactor);

                    // Compute dU / dBeta from expansion of equation (4-f)
                    realCosFactor = (dGdB * cnm + dHdB * snm) * cosPhi;
                    realSinFactor = (dGdB * snm - dHdB * cnm) * sinPhi;
                    dudbe += ran * Im * vmsn * gamMsn * kjn_1 * jacobi * (realCosFactor + realSinFactor);

                    // Compute dU / dGamma from expansion of equation (4-g)
                    realCosFactor = (gms * cnm + hms * snm) * cosPhi;
                    realSinFactor = (gms * snm - hms * cnm) * sinPhi;
                    dudga += ran * Im * vmsn * kjn_1 * (jacobi * dGamma + gamMsn * dJacobi) * (realCosFactor + realSinFactor);
                }
            }
            return new double[] {
                duda, dudh, dudk, dudl, dudal, dudbe, dudga
            };
        }
    }
}