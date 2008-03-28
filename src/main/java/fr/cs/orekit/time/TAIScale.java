package fr.cs.orekit.time;

/** International Atomic Time.
 * <p>This is a singleton class, so there is no public constructor.</p>
 * @author Luc Maisonobe
 * @see AbsoluteDate
 */
public class TAIScale extends TimeScale {

    /** Unique instance. */
    private static TimeScale instance = null;

    /** Private constructor for the singleton.
     */
    private TAIScale() {
        super("TAI");
    }

    /** Get the unique instance of this class.
     * @return the unique instance
     */
    public static TimeScale getInstance() {
        if (instance == null) {
            instance = new TAIScale();
        }
        return instance;
    }

    /** Get the offset to convert locations from {@link TAIScale} to instance.
     * @param taiTime location of an event in the {@link TAIScale} time scale
     * as a seconds index starting at 1970-01-01T00:00:00
     * @return offset to <em>add</em> to taiTime to get a location
     * in instance time scale
     */
    public double offsetFromTAI(double taiTime) {
        return 0;
    }

    /** Get the offset to convert locations from instance to {@link TAIScale}.
     * @param instanceTime location of an event in the instance time scale
     * as a seconds index starting at 1970-01-01T00:00:00
     * @return offset to <em>add</em> to instanceTime to get a location
     * in {@link TAIScale} time scale
     */
    public double offsetToTAI(double instanceTime) {
        return 0;
    }

}