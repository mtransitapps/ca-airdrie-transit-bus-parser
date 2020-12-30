package org.mtransit.parser.ca_airdrie_transit_bus;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mtransit.parser.CleanUtils;
import org.mtransit.parser.DefaultAgencyTools;
import org.mtransit.parser.MTLog;
import org.mtransit.parser.StringUtils;
import org.mtransit.parser.Utils;
import org.mtransit.parser.gtfs.data.GCalendar;
import org.mtransit.parser.gtfs.data.GCalendarDate;
import org.mtransit.parser.gtfs.data.GRoute;
import org.mtransit.parser.gtfs.data.GSpec;
import org.mtransit.parser.gtfs.data.GStop;
import org.mtransit.parser.gtfs.data.GTrip;
import org.mtransit.parser.mt.data.MAgency;
import org.mtransit.parser.mt.data.MRoute;
import org.mtransit.parser.mt.data.MTrip;

import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.mtransit.parser.Constants.EMPTY;

// https://data-airdrie.opendata.arcgis.com/datasets/e4625b7cf3634377a945d89e7d7c1fb3_0
// https://www.airdrie.ca/gettransitgtfs.cfm
// TODO real-time https://airdrietransit.transloc.com/
public class AirdrieTransitBusAgencyTools extends DefaultAgencyTools {

	public static void main(@Nullable String[] args) {
		if (args == null || args.length == 0) {
			args = new String[3];
			args[0] = "input/gtfs.zip";
			args[1] = "../../mtransitapps/ca-airdrie-transit-bus-android/res/raw/";
			args[2] = ""; // files-prefix
		}
		new AirdrieTransitBusAgencyTools().start(args);
	}

	@Nullable
	private HashSet<Integer> serviceIdInts;

	@Override
	public void start(@NotNull String[] args) {
		MTLog.log("Generating Airdrie Transit bus data...");
		long start = System.currentTimeMillis();
		this.serviceIdInts = extractUsefulServiceIdInts(args, this, true);
		super.start(args);
		MTLog.log("Generating Airdrie Transit bus data... DONE in %s.", Utils.getPrettyDuration(System.currentTimeMillis() - start));
	}

	@Override
	public boolean excludingAll() {
		return this.serviceIdInts != null && this.serviceIdInts.isEmpty();
	}

	@Override
	public boolean excludeCalendar(@NotNull GCalendar gCalendar) {
		if (this.serviceIdInts != null) {
			return excludeUselessCalendarInt(gCalendar, this.serviceIdInts);
		}
		return super.excludeCalendar(gCalendar);
	}

	@Override
	public boolean excludeCalendarDate(@NotNull GCalendarDate gCalendarDates) {
		if (this.serviceIdInts != null) {
			return excludeUselessCalendarDateInt(gCalendarDates, this.serviceIdInts);
		}
		return super.excludeCalendarDate(gCalendarDates);
	}

	@Override
	public boolean excludeTrip(@NotNull GTrip gTrip) {
		if (this.serviceIdInts != null) {
			return excludeUselessTripInt(gTrip, this.serviceIdInts);
		}
		return super.excludeTrip(gTrip);
	}

	@NotNull
	@Override
	public Integer getAgencyRouteType() {
		return MAgency.ROUTE_TYPE_BUS;
	}

	private static final Pattern DIGITS = Pattern.compile("[\\d]+");

	private static final long RID_ENDS_WITH_AM = 10_000L;
	private static final long RID_ENDS_WITH_PM = 20_000L;

	@Override
	public long getRouteId(@NotNull GRoute gRoute) {
		final String rsn = gRoute.getRouteShortName();
		if (!Utils.isDigitsOnly(rsn)) {
			final String rsnLC = rsn.toLowerCase(Locale.ENGLISH);
			Matcher matcher = DIGITS.matcher(rsn);
			if (matcher.find()) {
				int digits = Integer.parseInt(matcher.group());
				if (rsnLC.endsWith("am")) {
					return digits + RID_ENDS_WITH_AM;
				} else if (rsnLC.endsWith("pm")) {
					return digits + RID_ENDS_WITH_PM;
				}
			}
			if ("downtown ice service".equals(rsnLC)) {
				String rlnLC = gRoute.getRouteLongName().toLowerCase(Locale.ENGLISH);
				if (rlnLC.endsWith("morning")) {
					return 900 + RID_ENDS_WITH_AM;
				} else if (rlnLC.endsWith("afternoon")) {
					return 900 + RID_ENDS_WITH_PM;
				}
			}
			throw new MTLog.Fatal("Unexpected route ID for %s!", gRoute);
		}
		return Long.parseLong(rsn);
	}

	@Nullable
	@Override
	public String getRouteShortName(@NotNull GRoute gRoute) {
		final String rsn = gRoute.getRouteShortName();
		final String rsnLC = rsn.toLowerCase(Locale.ENGLISH);
		if ("downtown ice service".equals(rsnLC)) {
			String rlnLC = gRoute.getRouteLongName().toLowerCase(Locale.ENGLISH);
			if (rlnLC.endsWith("morning")) {
				return "D ICE AM";
			} else if (rlnLC.endsWith("afternoon")) {
				return "D ICE PM";
			}
		}
		return super.getRouteShortName(gRoute);
	}

	@Override
	public boolean mergeRouteLongName(@NotNull MRoute mRoute, @NotNull MRoute mRouteToMerge) {
		throw new MTLog.Fatal("Unexpected routes long name to merge: %s & %s!", mRoute, mRouteToMerge);
	}

	private static final String AGENCY_COLOR_BLUE = "0099CC"; // BLUE (from web site CSS)
	// private static final String AGENCY_COLOR_BLUE_DARK = "003399"; // BLUE DARK (from web site CSS)

	private static final String AGENCY_COLOR = AGENCY_COLOR_BLUE;

	@NotNull
	@Override
	public String getAgencyColor() {
		return AGENCY_COLOR;
	}

	@Override
	public void setTripHeadsign(@NotNull MRoute mRoute, @NotNull MTrip mTrip, @NotNull GTrip gTrip, @NotNull GSpec gtfs) {
		mTrip.setHeadsignString(
				cleanTripHeadsign(gTrip.getTripHeadsignOrDefault()),
				gTrip.getDirectionIdOrDefault()
		);
	}

	private static final Pattern INBOUND_OUTBOUND_ = CleanUtils.cleanWords("inbound", "outbound");

	private static final Pattern TRANSIT_TERMINAL_ = CleanUtils.cleanWords("transit terminal");
	private static final String TRANSIT_TERMINAL_REPLACEMENT = CleanUtils.cleanWordsReplacement("Term");

	private static final Pattern STARTS_WITH_BOUNDS = Pattern.compile("(^(eb|nb|sb|wb) )", Pattern.CASE_INSENSITIVE);

	private static final Pattern KEEP_INSIDE_PARENTHESES = Pattern.compile("(([^(]+)\\(([^)]+)\\))", Pattern.CASE_INSENSITIVE);
	private static final String KEEP_INSIDE_PARENTHESES_REPLACEMENT = "$3";

	private static final Pattern ENDS_WITH_DASH_ = Pattern.compile("( - .*$)*", Pattern.CASE_INSENSITIVE);

	@NotNull
	@Override
	public String cleanDirectionHeadsign(@NotNull String directionHeadSign) {
		directionHeadSign = super.cleanDirectionHeadsign(directionHeadSign);
		// ignore default trips head-signs
		directionHeadSign = INBOUND_OUTBOUND_.matcher(directionHeadSign).replaceAll(EMPTY);
		// clean stop name
		directionHeadSign = STARTS_WITH_BOUNDS.matcher(directionHeadSign).replaceAll(EMPTY);
		directionHeadSign = TRANSIT_TERMINAL_.matcher(directionHeadSign).replaceAll(TRANSIT_TERMINAL_REPLACEMENT);
		directionHeadSign = KEEP_INSIDE_PARENTHESES.matcher(directionHeadSign).replaceAll(KEEP_INSIDE_PARENTHESES_REPLACEMENT);
		directionHeadSign = ENDS_WITH_DASH_.matcher(directionHeadSign).replaceAll(EMPTY);
		return directionHeadSign;
	}

	@Override
	public boolean directionFinderEnabled() {
		return true;
	}

	@Override
	public boolean mergeHeadsign(@NotNull MTrip mTrip, @NotNull MTrip mTripToMerge) {
		throw new MTLog.Fatal("Unexpected trips to merge: %s & %s!", mTrip, mTripToMerge);
	}

	@NotNull
	@Override
	public String cleanTripHeadsign(@NotNull String tripHeadsign) {
		tripHeadsign = CleanUtils.cleanSlashes(tripHeadsign);
		tripHeadsign = CleanUtils.cleanNumbers(tripHeadsign);
		tripHeadsign = CleanUtils.cleanStreetTypes(tripHeadsign);
		return CleanUtils.cleanLabel(tripHeadsign);
	}

	@NotNull
	@Override
	public String cleanStopName(@NotNull String gStopName) {
		gStopName = CleanUtils.CLEAN_AND.matcher(gStopName).replaceAll(CleanUtils.CLEAN_AND_REPLACEMENT);
		gStopName = CleanUtils.CLEAN_AT.matcher(gStopName).replaceAll(CleanUtils.CLEAN_AT_REPLACEMENT);
		gStopName = CleanUtils.cleanSlashes(gStopName);
		gStopName = CleanUtils.removePoints(gStopName);
		gStopName = CleanUtils.cleanStreetTypes(gStopName);
		return CleanUtils.cleanLabel(gStopName);
	}

	@Override
	public int getStopId(@NotNull GStop gStop) {
		if (!StringUtils.isEmpty(gStop.getStopCode())
				&& Utils.isDigitsOnly(gStop.getStopCode())) {
			return Integer.parseInt(gStop.getStopCode()); // use stop code as stop ID
		}
		throw new MTLog.Fatal("Unexpected stop ID for %s!", gStop);
	}
}
