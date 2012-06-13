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
package org.orekit.propagation.analytical;


import java.io.IOException;
import java.text.ParseException;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.ode.nonstiff.AdaptiveStepsizeIntegrator;
import org.apache.commons.math3.ode.nonstiff.DormandPrince853Integrator;
import org.apache.commons.math3.util.FastMath;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.orekit.Utils;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.attitudes.LofOffset;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.errors.OrekitException;
import org.orekit.forces.gravity.CunninghamAttractionModel;
import org.orekit.forces.gravity.ThirdBodyAttraction;
import org.orekit.forces.gravity.potential.GravityFieldFactory;
import org.orekit.forces.gravity.potential.ICGEMFormatReader;
import org.orekit.forces.gravity.potential.PotentialCoefficientsProvider;
import org.orekit.forces.maneuvers.ConstantThrustManeuver;
import org.orekit.forces.maneuvers.SmallManeuverAnalyticalModel;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.LOFType;
import org.orekit.orbits.CircularOrbit;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.PositionAngle;
import org.orekit.propagation.BoundedPropagator;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.DateComponents;
import org.orekit.time.TimeComponents;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.PVCoordinates;

public class AdapterPropagatorTest {

    @Test
    public void testLowEarthOrbit() throws OrekitException, ParseException, IOException {

        Orbit leo = new CircularOrbit(7200000.0, -1.0e-5, 2.0e-4,
                                      FastMath.toRadians(98.0),
                                      FastMath.toRadians(123.456),
                                      0.0, PositionAngle.MEAN,
                                      FramesFactory.getEME2000(),
                                      new AbsoluteDate(new DateComponents(2004, 01, 01),
                                                       new TimeComponents(23, 30, 00.000),
                                                       TimeScalesFactory.getUTC()),
                                      Constants.EIGEN5C_EARTH_MU);
        double mass     = 5600.0;
        AbsoluteDate t0 = leo.getDate().shiftedBy(1000.0);
        Vector3D dV     = new Vector3D(-0.1, 0.2, 0.3);
        double f        = 20.0;
        double isp      = 315.0;
        double vExhaust = Constants.G0_STANDARD_GRAVITY * isp;
        double dt       = -(mass * vExhaust / f) * FastMath.expm1(-dV.getNorm() / vExhaust);
        BoundedPropagator withoutManeuver = getEphemeris(leo, mass, 5,
                                                         new LofOffset(leo.getFrame(), LOFType.LVLH),
                                                         t0, Vector3D.ZERO, f, isp,
                                                         false, false, null, 0, 0);
        BoundedPropagator withManeuver    = getEphemeris(leo, mass, 5,
                                                         new LofOffset(leo.getFrame(), LOFType.LVLH),
                                                         t0, dV, f, isp,
                                                         false, false, null, 0, 0);

        // we set up a model that reverts the maneuvers
        AdapterPropagator adapterPropagator = new AdapterPropagator(withManeuver);
        AdapterPropagator.DifferentialEffect effect =
                new SmallManeuverAnalyticalModel(adapterPropagator.propagate(t0), dV.negate(), isp);
        adapterPropagator.addEffect(effect);

        for (AbsoluteDate t = t0.shiftedBy(0.5 * dt);
             t.compareTo(withoutManeuver.getMaxDate()) < 0;
             t = t.shiftedBy(60.0)) {
            PVCoordinates pvWithout  = withoutManeuver.getPVCoordinates(t, leo.getFrame());
            PVCoordinates pvReverted = adapterPropagator.getPVCoordinates(t, leo.getFrame());
            double revertError       = new PVCoordinates(pvWithout, pvReverted).getPosition().getNorm();
            Assert.assertEquals(0, revertError, 0.45);
        }

    }

    @Test
    public void testEccentricOrbit() throws OrekitException, ParseException, IOException {

        Orbit heo = new KeplerianOrbit(90000000.0, 0.92, FastMath.toRadians(98.0),
                                       FastMath.toRadians(12.3456),
                                       FastMath.toRadians(123.456),
                                       FastMath.toRadians(1.23456), PositionAngle.MEAN,
                                       FramesFactory.getEME2000(),
                                       new AbsoluteDate(new DateComponents(2004, 01, 01),
                                                        new TimeComponents(23, 30, 00.000),
                                                        TimeScalesFactory.getUTC()),
                                                        Constants.EIGEN5C_EARTH_MU);
        double mass     = 5600.0;
        AbsoluteDate t0 = heo.getDate().shiftedBy(1000.0);
        Vector3D dV     = new Vector3D(-0.01, 0.02, 0.03);
        double f        = 20.0;
        double isp      = 315.0;
        double vExhaust = Constants.G0_STANDARD_GRAVITY * isp;
        double dt       = -(mass * vExhaust / f) * FastMath.expm1(-dV.getNorm() / vExhaust);
        BoundedPropagator withoutManeuver = getEphemeris(heo, mass, 5,
                                                         new LofOffset(heo.getFrame(), LOFType.LVLH),
                                                         t0, Vector3D.ZERO, f, isp,
                                                         false, false, null, 0, 0);
        BoundedPropagator withManeuver    = getEphemeris(heo, mass, 5,
                                                         new LofOffset(heo.getFrame(), LOFType.LVLH),
                                                         t0, dV, f, isp,
                                                         false, false, null, 0, 0);

        // we set up a model that reverts the maneuvers
        AdapterPropagator adapterPropagator = new AdapterPropagator(withManeuver);
        AdapterPropagator.DifferentialEffect effect =
                new SmallManeuverAnalyticalModel(adapterPropagator.propagate(t0), dV.negate(), isp);
        adapterPropagator.addEffect(effect);

        for (AbsoluteDate t = t0.shiftedBy(0.5 * dt);
             t.compareTo(withoutManeuver.getMaxDate()) < 0;
             t = t.shiftedBy(300.0)) {
            PVCoordinates pvWithout  = withoutManeuver.getPVCoordinates(t, heo.getFrame());
            PVCoordinates pvReverted = adapterPropagator.getPVCoordinates(t, heo.getFrame());
            double revertError       = new PVCoordinates(pvWithout, pvReverted).getPosition().getNorm();
            Assert.assertEquals(0, revertError, 180.0);
        }

    }

    @Test
    public void testNonKeplerian() throws OrekitException, ParseException, IOException {

        Orbit leo = new CircularOrbit(7204319.233600575, 4.434564637450575E-4, 0.0011736728299091088,
                                      1.7211611441767323, 5.5552084166959474,
                                      24950.321259193086, PositionAngle.TRUE,
                                      FramesFactory.getEME2000(),
                                      new AbsoluteDate(new DateComponents(2003, 9, 16),
                                                       new TimeComponents(23, 11, 20.264),
                                                       TimeScalesFactory.getUTC()),
                                      Constants.EIGEN5C_EARTH_MU);
        double mass     = 4093.0;
        AbsoluteDate t0 = new AbsoluteDate(new DateComponents(2003, 9, 16),
                                           new TimeComponents(23, 14, 40.264),
                                           TimeScalesFactory.getUTC());
        Vector3D dV     = new Vector3D(0.0, 3.0, 0.0);
        double f        = 40.0;
        double isp      = 300.0;
        double vExhaust = Constants.G0_STANDARD_GRAVITY * isp;
        double dt       = -(mass * vExhaust / f) * FastMath.expm1(-dV.getNorm() / vExhaust);
        // setup a specific coefficient file for gravity potential as it will also
        // try to read a corrupted one otherwise
        GravityFieldFactory.addPotentialCoefficientsReader(new ICGEMFormatReader("g007_eigen_05c_coef", false));
        PotentialCoefficientsProvider gravityField = GravityFieldFactory.getPotentialProvider();
        BoundedPropagator withoutManeuver = getEphemeris(leo, mass, 10,
                                                         new LofOffset(leo.getFrame(), LOFType.VNC),
                                                         t0, Vector3D.ZERO, f, isp,
                                                         true, true, gravityField, 8, 8);
        BoundedPropagator withManeuver    = getEphemeris(leo, mass, 10,
                                                         new LofOffset(leo.getFrame(), LOFType.VNC),
                                                         t0, dV, f, isp,
                                                         true, true, gravityField, 8, 8);

        // we set up a model that reverts the maneuvers
        AdapterPropagator adapterPropagator = new AdapterPropagator(withManeuver);
        SpacecraftState state0 = adapterPropagator.propagate(t0);
        AdapterPropagator.DifferentialEffect directEffect =
                new SmallManeuverAnalyticalModel(state0, dV.negate(), isp);
        AdapterPropagator.DifferentialEffect derivedEffect =
                new J2DifferentialEffect(state0, directEffect, false, gravityField);
        adapterPropagator.addEffect(directEffect);
        adapterPropagator.addEffect(derivedEffect);

        double maxDelta = 0;
        double maxNominal = 0;
        for (AbsoluteDate t = t0.shiftedBy(0.5 * dt);
             t.compareTo(withoutManeuver.getMaxDate()) < 0;
             t = t.shiftedBy(600.0)) {
            PVCoordinates pvWithout  = withoutManeuver.getPVCoordinates(t, leo.getFrame());
            PVCoordinates pvWith     = withManeuver.getPVCoordinates(t, leo.getFrame());
            PVCoordinates pvReverted = adapterPropagator.getPVCoordinates(t, leo.getFrame());
            double nominal           = new PVCoordinates(pvWithout, pvWith).getPosition().getNorm();
            double revertError       = new PVCoordinates(pvWithout, pvReverted).getPosition().getNorm();
            maxDelta = FastMath.max(maxDelta, revertError);
            maxNominal = FastMath.max(maxNominal, nominal);
        }
        Assert.assertTrue(maxDelta   < 120);
        Assert.assertTrue(maxNominal > 2800);

    }

    private BoundedPropagator getEphemeris(final Orbit orbit, final double mass, final int nbOrbits,
                                           final AttitudeProvider law,
                                           final AbsoluteDate t0, final Vector3D dV,
                                           final double f, final double isp,
                                           final boolean sunAttraction, final boolean moonAttraction,
                                           final PotentialCoefficientsProvider gravityField,
                                           final int degree, final int order)
        throws OrekitException, ParseException, IOException {

        final SpacecraftState initialState =
            new SpacecraftState(orbit, law.getAttitude(orbit, orbit.getDate(), orbit.getFrame()), mass);


        // set up numerical propagator
        final double dP = 1.0;
        double[][] tolerances = NumericalPropagator.tolerances(dP, orbit, orbit.getType());
        AdaptiveStepsizeIntegrator integrator =
            new DormandPrince853Integrator(0.001, 1000, tolerances[0], tolerances[1]);
        integrator.setInitialStepSize(orbit.getKeplerianPeriod() / 100.0);
        final NumericalPropagator propagator = new NumericalPropagator(integrator);
        propagator.setInitialState(initialState);
        propagator.setAttitudeProvider(law);

        if (dV.getNorm() > 1.0e-6) {
            // set up maneuver
            final double vExhaust = Constants.G0_STANDARD_GRAVITY * isp;
            final double dt = -(mass * vExhaust / f) * FastMath.expm1(-dV.getNorm() / vExhaust);
            final ConstantThrustManeuver maneuver =
                    new ConstantThrustManeuver(t0.shiftedBy(-0.5 * dt), dt , f, isp, dV.normalize());
            propagator.addForceModel(maneuver);
        }

        if (sunAttraction) {
            propagator.addForceModel(new ThirdBodyAttraction(CelestialBodyFactory.getSun()));
        }

        if (moonAttraction) {
            propagator.addForceModel(new ThirdBodyAttraction(CelestialBodyFactory.getMoon()));
        }

        if (gravityField != null) {
            propagator.addForceModel(new CunninghamAttractionModel(FramesFactory.getGTOD(false),
                                                                   gravityField.getAe(),
                                                                   gravityField.getMu(),
                                                                   gravityField.getC(degree, order, false),
                                                                   gravityField.getS(degree, order, false)));
        }

        propagator.setEphemerisMode();
        propagator.propagate(t0.shiftedBy(nbOrbits * orbit.getKeplerianPeriod()));
        return propagator.getGeneratedEphemeris();

    }

    @Before
    public void setUp() {
        Utils.setDataRoot("regular-data:potential/icgem-format");
    }

}