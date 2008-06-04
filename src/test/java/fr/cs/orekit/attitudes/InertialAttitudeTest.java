package fr.cs.orekit.attitudes;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.commons.math.geometry.Rotation;
import org.apache.commons.math.geometry.Vector3D;

import fr.cs.orekit.errors.OrekitException;
import fr.cs.orekit.frames.Frame;
import fr.cs.orekit.orbits.KeplerianOrbit;
import fr.cs.orekit.orbits.Orbit;
import fr.cs.orekit.propagation.SpacecraftState;
import fr.cs.orekit.propagation.analytical.KeplerianPropagator;
import fr.cs.orekit.time.AbsoluteDate;
import fr.cs.orekit.time.ChunkedDate;
import fr.cs.orekit.time.ChunkedTime;
import fr.cs.orekit.time.UTCScale;
import fr.cs.orekit.utils.Line;
import fr.cs.orekit.utils.PVCoordinates;

public class InertialAttitudeTest extends TestCase {

    private AbsoluteDate t0;
    private Orbit        orbit0;

    public InertialAttitudeTest(String name) {
        super(name);
    }

    public void testIsInertial() throws OrekitException {
        InertialLaw law = new InertialLaw(new Rotation(new Vector3D(0.6, 0.48, 0.64), 0.9));
        KeplerianPropagator propagator = new KeplerianPropagator(orbit0, law);
        Attitude initial = propagator.propagate(t0).getAttitude();
        for (double t = 0; t < 10000.0; t += 100) {
            Attitude attitude =
                propagator.propagate(new AbsoluteDate(t0, t)).getAttitude();
            Rotation evolution = attitude.getRotation().applyTo(initial.getRotation().revert());
            assertEquals(0, evolution.getAngle(), 1.0e-10);
        }
    }

    public void testCompensateMomentum() throws OrekitException {
        InertialLaw law = new InertialLaw(new Rotation(new Vector3D(-0.64, 0.6, 0.48), 0.2));
        KeplerianPropagator propagator = new KeplerianPropagator(orbit0, law);
        Attitude initial = propagator.propagate(t0).getAttitude();
        for (double t = 0; t < 10000.0; t += 100) {
            Attitude attitude =
                propagator.propagate(new AbsoluteDate(t0, t)).getAttitude();
            Rotation evolution = attitude.getRotation().applyTo(initial.getRotation().revert());
            assertEquals(0, evolution.getAngle(), 1.0e-10);
        }
    }

    public void setUp() {
        try {
        t0 = new AbsoluteDate(new ChunkedDate(2008, 06, 03), ChunkedTime.H12,
                              UTCScale.getInstance());
        orbit0 =
            new KeplerianOrbit(12345678.9, 0.001, 2.3, 0.1, 3.04, 2.4,
                               KeplerianOrbit.TRUE_ANOMALY, Frame.getJ2000(),
                               t0, 3.986004415e14);
        } catch (OrekitException oe) {
            fail(oe.getMessage());
        }
    }

    public void testDummy() {
    }

    public void tearDown() {
        t0     = null;
        orbit0 = null;
    }

    public static Test suite() {
        return new TestSuite(InertialAttitudeTest.class);
    }

}
