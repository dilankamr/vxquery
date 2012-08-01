package org.apache.vxquery.datamodel.accessors.atomic;

import org.apache.vxquery.datamodel.api.IDate;
import org.apache.vxquery.datamodel.api.ITime;
import org.apache.vxquery.datamodel.api.ITimezone;
import org.apache.vxquery.datamodel.util.DateTime;

import edu.uci.ics.hyracks.api.dataflow.value.ITypeTraits;
import edu.uci.ics.hyracks.data.std.api.AbstractPointable;
import edu.uci.ics.hyracks.data.std.api.IPointable;
import edu.uci.ics.hyracks.data.std.api.IPointableFactory;
import edu.uci.ics.hyracks.data.std.primitive.BytePointable;
import edu.uci.ics.hyracks.data.std.primitive.IntegerPointable;
import edu.uci.ics.hyracks.data.std.primitive.ShortPointable;

/**
 * The datetime is split up into eight sections. Due to leap year, we have decided to keep the
 * storage simple by saving each datetime section separately. For calculations you can access
 * YearMonth (months) and DayTime (milliseconds) values.
 * 
 * @author prestoncarman
 */
public class XSDateTimePointable extends AbstractPointable implements IDate, ITime, ITimezone {
    public final static int YEAR_OFFSET = 0;
    public final static int MONTH_OFFSET = 2;
    public final static int DAY_OFFSET = 3;
    public final static int HOUR_OFFSET = 4;
    public final static int MINUTE_OFFSET = 5;
    public final static int MILLISECOND_OFFSET = 6;
    public final static int TIMEZONE_HOUR_OFFSET = 10;
    public final static int TIMEZONE_MINUTE_OFFSET = 11;

    public static final ITypeTraits TYPE_TRAITS = new ITypeTraits() {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isFixedLength() {
            return true;
        }

        @Override
        public int getFixedLength() {
            return 12;
        }
    };

    public static final IPointableFactory FACTORY = new IPointableFactory() {
        private static final long serialVersionUID = 1L;

        @Override
        public IPointable createPointable() {
            return new XSDateTimePointable();
        }

        @Override
        public ITypeTraits getTypeTraits() {
            return TYPE_TRAITS;
        }
    };

    public void setDateTime(long year, long month, long day, long hour, long minute, long milliSecond,
            long timezoneHour, long timezoneMinute) {
        setDateTime(bytes, start, year, month, day, hour, minute, milliSecond, timezoneHour, timezoneMinute);
    }

    public static void setDateTime(byte[] bytes, int start, long year, long month, long day, long hour, long minute,
            long milliSecond, long timezoneHour, long timezoneMinute) {
        ShortPointable.setShort(bytes, start + YEAR_OFFSET, (short) year);
        BytePointable.setByte(bytes, start + MONTH_OFFSET, (byte) month);
        BytePointable.setByte(bytes, start + DAY_OFFSET, (byte) day);
        BytePointable.setByte(bytes, start + HOUR_OFFSET, (byte) hour);
        BytePointable.setByte(bytes, start + MINUTE_OFFSET, (byte) minute);
        IntegerPointable.setInteger(bytes, start + MILLISECOND_OFFSET, (int) milliSecond);
        BytePointable.setByte(bytes, start + TIMEZONE_HOUR_OFFSET, (byte) timezoneHour);
        BytePointable.setByte(bytes, start + TIMEZONE_MINUTE_OFFSET, (byte) timezoneMinute);
    }

    @Override
    public long getYear() {
        return getYear(bytes, start);
    }

    public static long getYear(byte[] bytes, int start) {
        return (long) ShortPointable.getShort(bytes, start + YEAR_OFFSET);
    }

    @Override
    public long getMonth() {
        return getMonth(bytes, start);
    }

    public static long getMonth(byte[] bytes, int start) {
        return (long) BytePointable.getByte(bytes, start + MONTH_OFFSET);
    }

    @Override
    public long getDay() {
        return getDay(bytes, start);
    }

    public static long getDay(byte[] bytes, int start) {
        return (long) BytePointable.getByte(bytes, start + DAY_OFFSET);
    }

    @Override
    public long getHour() {
        return getHour(bytes, start);
    }

    public static long getHour(byte[] bytes, int start) {
        return (long) BytePointable.getByte(bytes, start + HOUR_OFFSET);
    }

    @Override
    public long getMinute() {
        return getMinute(bytes, start);
    }

    public static long getMinute(byte[] bytes, int start) {
        return (long) BytePointable.getByte(bytes, start + MINUTE_OFFSET);
    }

    @Override
    public long getMilliSecond() {
        return getMilliSecond(bytes, start);
    }

    public static long getMilliSecond(byte[] bytes, int start) {
        return (long) IntegerPointable.getInteger(bytes, start + MILLISECOND_OFFSET);
    }

    @Override
    public long getTimezoneHour() {
        return getTimezoneHour(bytes, start);
    }

    public static long getTimezoneHour(byte[] bytes, int start) {
        return (long) BytePointable.getByte(bytes, start + TIMEZONE_HOUR_OFFSET);
    }

    @Override
    public long getTimezoneMinute() {
        return getTimezoneMinute(bytes, start);
    }

    public static long getTimezoneMinute(byte[] bytes, int start) {
        return (long) BytePointable.getByte(bytes, start + TIMEZONE_MINUTE_OFFSET);
    }

    @Override
    public long getTimezone() {
        return getTimezone(bytes, start);
    }

    public static long getTimezone(byte[] bytes, int start) {
        return (getTimezoneHour(bytes, start) * 60 + getTimezoneMinute(bytes, start));
    }

    @Override
    public long getYearMonth() {
        return getYearMonth(bytes, start);
    }

    public static long getYearMonth(byte[] bytes, int start) {
        return (getYear(bytes, start) * 12 + getMonth(bytes, start));
    }

    @Override
    public long getDayTime() {
        return getDayTime(bytes, start);
    }

    public static long getDayTime(byte[] bytes, int start) {
        return (getDay(bytes, start) * DateTime.CHRONON_OF_DAY + getHour(bytes, start) * DateTime.CHRONON_OF_HOUR
                + getMinute(bytes, start) * DateTime.CHRONON_OF_MINUTE + getMilliSecond(bytes, start));
    }

    public String toString() {
        return toString(bytes, start);
    }

    public static String toString(byte[] bytes, int start) {
        return getMonth(bytes, start)
                + "-"
                + getDay(bytes, start)
                + "-"
                + getYear(bytes, start)
                + "T"
                + getHour(bytes, start)
                + ":"
                + getMinute(bytes, start)
                + ":"
                + getMilliSecond(bytes, start)
                + (getTimezoneHour(bytes, start) != DateTime.TIMEZONE_HOUR_NULL
                        && getTimezoneMinute(bytes, start) != DateTime.TIMEZONE_MINUTE_NULL ? (getTimezoneHour(bytes,
                        start) < 0 || getTimezoneMinute(bytes, start) < 0 ? "-" : "+")
                        + getTimezoneHour(bytes, start) + ":" + getTimezoneMinute(bytes, start) : "");
    }

}