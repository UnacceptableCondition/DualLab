import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

public class TimetableUtil {

    // region settings
    private static final String IN_DATA_FILE_PATH = ""; // file path should be added

    private static final String OUT_DATA_FILE_PATH = ""; // file path should be added

    // key - company name, value - output priority
    private static final Map<String, Integer> AVAILABLE_COMPANY_NAMES = new HashMap<String, Integer>() {{
        put("Posh", 0);
        put("Grotty", 1);
    }};
    // endregion

    private static final BiPredicate<Integer, Integer> isTimeRangeValid = (hours, minutes) ->
            hours <= 23 && hours >= 0 && minutes <= 59 && minutes >= 0;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void main(String[] args) {
        try (
                Stream<String> stream = Files.lines(Paths.get(IN_DATA_FILE_PATH));
                PrintWriter printWriter = new PrintWriter(new FileWriter(OUT_DATA_FILE_PATH));
        ) {
            TreeSet<TimeTableRaw> resultRaws = new TreeSet<>();
            stream
                    .map(TimetableUtil::parseDataLine)
                    .map(v -> v.filter(r -> (r.endHour * 60 + r.endMinutes) - (r.startHour * 60 + r.startMinutes) <= 60))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .sorted(Comparator.comparingLong(TimeTableRaw::getKey))
                    .reduce((current, next) -> timeTableRawReducer(current, next, resultRaws));

            formatPrint(printWriter, resultRaws, AVAILABLE_COMPANY_NAMES.keySet());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static TimeTableRaw timeTableRawReducer(TimeTableRaw current,
                                                    TimeTableRaw next,
                                                    Collection<TimeTableRaw> collection) {
        // 59m - max time interval which indicates that there can be an overlay between two schedules
        if (current.getKey() == next.getKey() || current.getKey() + 59 >= next.getKey()) {
            if (current.getStartTime() == next.getStartTime() && current.getEndTime() == next.getEndTime()) {
                if (AVAILABLE_COMPANY_NAMES.get(current.companyName) < AVAILABLE_COMPANY_NAMES.get(next.companyName)) {
                    collection.add(current);
                    return current;
                } else {
                    collection.remove(current);
                    collection.add(next);
                    return next;
                }
            }

            if (current.getStartTime() == next.getStartTime()) {
                if (current.getEndTime() < next.getEndTime()) {
                    collection.add(current);
                    return current;
                } else {
                    collection.remove(current);
                    collection.add(next);
                    return next;
                }
            }

            if (current.getEndTime() == next.getEndTime()) {
                if (current.getStartTime() < next.getStartTime()) {
                    collection.remove(current);
                    collection.add(next);
                    return next;
                } else {
                    collection.add(current);
                    return current;
                }
            }

            if (next.getStartTime() > current.getStartTime() && next.getEndTime() < current.getEndTime()) {
                collection.remove(current);
                collection.add(next);
                return next;
            }
        }
        collection.add(next);
        return next;
    }

    private static Optional<TimeTableRaw> parseDataLine(String line) {
        Optional<TimeTableRaw> result = Optional.empty();
        String[] data = line.split(" ");
        if (data.length == 3) {
            result = parseTime(data[1])
                    .flatMap(startTime ->
                            parseTime(data[2]).map(endTime -> buildTimeTableRaw(data[0], startTime, endTime))
                    )
                    .orElse(Optional.empty());
        }

        return result;
    }

    private static Optional<TimeTableRaw> buildTimeTableRaw(String companyName,
                                                            Tuple<Integer, Integer> startTime,
                                                            Tuple<Integer, Integer> endTime) {
        Optional<TimeTableRaw> result = Optional.empty();
        if (AVAILABLE_COMPANY_NAMES.containsKey(companyName)) {
            result = Optional.of(new TimeTableRaw(companyName, startTime.x, startTime.y, endTime.x, endTime.y));
        }

        return result;
    }

    /**
     * @return an empty Optional if there are parsing errors
     * */
    private static Optional<Tuple<Integer, Integer>> parseTime(String timeData) {
        Optional<Tuple<Integer, Integer>> result = Optional.empty();
        String[] times = timeData.split(":");
        if (times.length == 2) {
            try {
                int hours = Integer.parseInt(times[0]);
                int minutes = Integer.parseInt(times[1]);
                if (isTimeRangeValid.test(hours, minutes)) {
                    result = Optional.of(new Tuple<>(hours, minutes));
                }
            } catch (NumberFormatException ignored) { /* for logging */ }
        }

        return result;
    }

    private static void formatPrint(PrintWriter writer, Collection<TimeTableRaw> data, Collection<String> delimiters) {
        delimiters.forEach( delimiter -> {
            data.stream()
                    .filter( raw -> raw.companyName.equals(delimiter))
                    .forEach(writer::println);
            writer.println("");
        });
    }

    private static class TimeTableRaw implements Comparable<TimeTableRaw> {
        final String companyName;
        final int startHour;
        final int startMinutes;
        final int endHour;
        final int endMinutes;

        public TimeTableRaw(String companyName, int startHour, int startMinutes, int endHour, int endMinutes) {
            this.companyName = companyName;
            this.startHour = startHour;
            this.startMinutes = startMinutes;
            this.endHour = endHour;
            this.endMinutes = endMinutes;
        }

        public int getStartTime() {
            return startHour * 60 + startMinutes;
        }

        public int getEndTime() {
            return endHour * 60 + endMinutes;
        }

        @Override
        public String toString() {
            return String.format(
                    "%s %s:%s %s:%s",
                    companyName,
                    startHour < 10 ? "0" + startHour : startHour,
                    startMinutes < 10 ? "0" + startMinutes : startMinutes,
                    endHour < 10 ? "0" + endHour : endHour,
                    endMinutes < 10 ? "0" + endMinutes : endMinutes
            );
        }

        // It is used like hash
        public long getKey() {
            return getStartTime() + getEndTime();
        }

        @Override
        public int compareTo(TimeTableRaw timeTableRaw) {
            return this.getStartTime() - timeTableRaw.getStartTime();
        }
    }

    public static class Tuple<X, Y> {
        final X x;
        final Y y;

        public Tuple(X x, Y y) {
            this.x = x;
            this.y = y;
        }
    }
}
