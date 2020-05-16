package org.mtransit.parser.ca_airdrie_transit_bus;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mtransit.parser.CleanUtils;
import org.mtransit.parser.DefaultAgencyTools;
import org.mtransit.parser.MTLog;
import org.mtransit.parser.Pair;
import org.mtransit.parser.SplitUtils;
import org.mtransit.parser.SplitUtils.RouteTripSpec;
import org.mtransit.parser.StringUtils;
import org.mtransit.parser.Utils;
import org.mtransit.parser.gtfs.data.GCalendar;
import org.mtransit.parser.gtfs.data.GCalendarDate;
import org.mtransit.parser.gtfs.data.GRoute;
import org.mtransit.parser.gtfs.data.GSpec;
import org.mtransit.parser.gtfs.data.GStop;
import org.mtransit.parser.gtfs.data.GTrip;
import org.mtransit.parser.gtfs.data.GTripStop;
import org.mtransit.parser.mt.data.MAgency;
import org.mtransit.parser.mt.data.MInboundType;
import org.mtransit.parser.mt.data.MRoute;
import org.mtransit.parser.mt.data.MTrip;
import org.mtransit.parser.mt.data.MTripStop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// http://www.calgaryregionopendata.ca/browse/file/10090
// http://www.airdrie.ca/gettransitgtfs.cfm
// TODO real-time http://airdrietransit.transloc.com/
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

	private HashSet<String> serviceIds;

	@Override
	public void start(@NotNull String[] args) {
		MTLog.log("Generating Airdrie Transit bus data...");
		long start = System.currentTimeMillis();
		this.serviceIds = extractUsefulServiceIds(args, this, true);
		super.start(args);
		MTLog.log("Generating Airdrie Transit bus data... DONE in %s.", Utils.getPrettyDuration(System.currentTimeMillis() - start));
	}

	@Override
	public boolean excludingAll() {
		return this.serviceIds != null && this.serviceIds.isEmpty();
	}

	@Override
	public boolean excludeCalendar(@NotNull GCalendar gCalendar) {
		if (this.serviceIds != null) {
			return excludeUselessCalendar(gCalendar, this.serviceIds);
		}
		return super.excludeCalendar(gCalendar);
	}

	@Override
	public boolean excludeCalendarDate(@NotNull GCalendarDate gCalendarDates) {
		if (this.serviceIds != null) {
			return excludeUselessCalendarDate(gCalendarDates, this.serviceIds);
		}
		return super.excludeCalendarDate(gCalendarDates);
	}

	@Override
	public boolean excludeTrip(@NotNull GTrip gTrip) {
		if (this.serviceIds != null) {
			return excludeUselessTrip(gTrip, this.serviceIds);
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
		if (!Utils.isDigitsOnly(gRoute.getRouteShortName())) {
			Matcher matcher = DIGITS.matcher(gRoute.getRouteShortName());
			if (matcher.find()) {
				int digits = Integer.parseInt(matcher.group());
				String rsn = gRoute.getRouteShortName().toLowerCase(Locale.ENGLISH);
				if (rsn.endsWith("am")) {
					return digits + RID_ENDS_WITH_AM;
				} else if (rsn.endsWith("pm")) {
					return digits + RID_ENDS_WITH_PM;
				}
				throw new MTLog.Fatal("Unexpected route ID for %s!", gRoute);
			}
		}
		return Long.parseLong(gRoute.getRouteShortName());
	}

	@Nullable
	@Override
	public String getRouteShortName(@NotNull GRoute gRoute) {
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

	private static final String AIRDRIE = "Airdrie";
	private static final String CALGARY = "Calgary";
	private static final String MC_KNIGHT = "McKnight";
	private static final String SAGEWOOD = "Sagewood";
	private static final String SIERRA_SPRINGS = "Sierra Spgs";
	private static final String REUNION = "Reunion";
	private static final String GENESIS_PLACE = "Genesis Pl";
	private static final String _8TH_STREET = "8th St";

	private static HashMap<Long, RouteTripSpec> ALL_ROUTE_TRIPS2;

	static {
		//noinspection UnnecessaryLocalVariable
		HashMap<Long, RouteTripSpec> map2 = new HashMap<>();
		ALL_ROUTE_TRIPS2 = map2;
	}

	@Override
	public int compareEarly(long routeId,
							@NotNull List<MTripStop> list1, @NotNull List<MTripStop> list2,
							@NotNull MTripStop ts1, @NotNull MTripStop ts2,
							@NotNull GStop ts1GStop, @NotNull GStop ts2GStop) {
		if (ALL_ROUTE_TRIPS2.containsKey(routeId)) {
			return ALL_ROUTE_TRIPS2.get(routeId).compare(routeId, list1, list2, ts1, ts2, ts1GStop, ts2GStop, this);
		}
		return super.compareEarly(routeId, list1, list2, ts1, ts2, ts1GStop, ts2GStop);
	}

	@NotNull
	@Override
	public ArrayList<MTrip> splitTrip(@NotNull MRoute mRoute, @Nullable GTrip gTrip, @NotNull GSpec gtfs) {
		if (ALL_ROUTE_TRIPS2.containsKey(mRoute.getId())) {
			return ALL_ROUTE_TRIPS2.get(mRoute.getId()).getAllTrips();
		}
		return super.splitTrip(mRoute, gTrip, gtfs);
	}

	@NotNull
	@Override
	public Pair<Long[], Integer[]> splitTripStop(@NotNull MRoute mRoute, @NotNull GTrip gTrip, @NotNull GTripStop gTripStop, @NotNull ArrayList<MTrip> splitTrips, @NotNull GSpec routeGTFS) {
		if (ALL_ROUTE_TRIPS2.containsKey(mRoute.getId())) {
			return SplitUtils.splitTripStop(mRoute, gTrip, gTripStop, routeGTFS, ALL_ROUTE_TRIPS2.get(mRoute.getId()), this);
		}
		return super.splitTripStop(mRoute, gTrip, gTripStop, splitTrips, routeGTFS);
	}

	@Override
	public void setTripHeadsign(@NotNull MRoute mRoute, @NotNull MTrip mTrip, @NotNull GTrip gTrip, @NotNull GSpec gtfs) {
		if (ALL_ROUTE_TRIPS2.containsKey(mRoute.getId())) {
			return; // split
		}
		final int directionId = gTrip.getDirectionIdOrDefault();
		if (mRoute.getId() == 1L) {
			if (directionId == MInboundType.INBOUND.intValue()) {
				mTrip.setHeadsignString(SAGEWOOD, directionId);
				return;
			} else if (directionId == MInboundType.OUTBOUND.intValue()) {
				mTrip.setHeadsignString(GENESIS_PLACE, directionId);
				return;
			}
		} else if (mRoute.getId() == 2L) {
			if (directionId == MInboundType.INBOUND.intValue()) {
				mTrip.setHeadsignString(REUNION, directionId);
				return;
			} else if (directionId == MInboundType.OUTBOUND.intValue()) {
				mTrip.setHeadsignString(SIERRA_SPRINGS, directionId);
				return;
			}
		} else if (mRoute.getId() == 3L) {
			if (directionId == MInboundType.INBOUND.intValue()) {
				mTrip.setHeadsignString(GENESIS_PLACE, directionId);
				return;
			} else if (directionId == MInboundType.OUTBOUND.intValue()) {
				mTrip.setHeadsignString(_8TH_STREET, directionId);
				return;
			}
		} else if (mRoute.getId() == 900L) {
			if (directionId == MInboundType.INBOUND.intValue()) {
				mTrip.setHeadsignString(AIRDRIE, directionId);
				return;
			} else if (directionId == MInboundType.OUTBOUND.intValue()) {
				mTrip.setHeadsignString(MC_KNIGHT, directionId);
				return;
			}
		} else if (mRoute.getId() == 901L + RID_ENDS_WITH_AM) { // 901AM
			if (directionId == MInboundType.INBOUND.intValue()) {
				mTrip.setHeadsignString(AIRDRIE, directionId);
				return;
			} else if (directionId == MInboundType.OUTBOUND.intValue()) {
				mTrip.setHeadsignString(CALGARY, directionId);
				return;
			}
		} else if (mRoute.getId() == 901L + RID_ENDS_WITH_PM) { // 901PM
			if (directionId == MInboundType.INBOUND.intValue()) {
				mTrip.setHeadsignString(AIRDRIE, directionId);
				return;
			} else if (directionId == MInboundType.OUTBOUND.intValue()) {
				mTrip.setHeadsignString(CALGARY, directionId);
				return;
			}
		} else if (mRoute.getId() == 902L + RID_ENDS_WITH_AM) { // 902AM
			if (directionId == MInboundType.INBOUND.intValue()) {
				mTrip.setHeadsignString(AIRDRIE, directionId);
				return;
			} else if (directionId == MInboundType.OUTBOUND.intValue()) {
				mTrip.setHeadsignString(CALGARY, directionId);
				return;
			}
		} else if (mRoute.getId() == 902L + RID_ENDS_WITH_PM) { // 902PM
			if (directionId == MInboundType.INBOUND.intValue()) {
				mTrip.setHeadsignString(AIRDRIE, directionId);
				return;
			} else if (directionId == MInboundType.OUTBOUND.intValue()) {
				mTrip.setHeadsignString(CALGARY, directionId);
				return;
			}
		}
		mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), directionId);
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

	private static final Pattern STARTS_WITH_BOUNDS = Pattern.compile("(^(eb|nb|sb|wb) )*", Pattern.CASE_INSENSITIVE);

	@NotNull
	@Override
	public String cleanStopName(@NotNull String gStopName) {
		gStopName = STARTS_WITH_BOUNDS.matcher(gStopName).replaceAll(StringUtils.EMPTY);
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
