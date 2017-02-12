package org.mtransit.parser.ca_airdrie_transit_bus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.mtransit.parser.CleanUtils;
import org.mtransit.parser.DefaultAgencyTools;
import org.mtransit.parser.Pair;
import org.mtransit.parser.SplitUtils;
import org.mtransit.parser.SplitUtils.RouteTripSpec;
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

// http://www.calgaryregionopendata.ca/browse/file/10090
// http://www.airdrie.ca/gettransitgtfs.cfm
// TODO real-time http://airdrietransit.transloc.com/
public class AirdrieTransitBusAgencyTools extends DefaultAgencyTools {

	public static void main(String[] args) {
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
	public void start(String[] args) {
		System.out.printf("\nGenerating Airdrie Transit bus data...");
		long start = System.currentTimeMillis();
		this.serviceIds = extractUsefulServiceIds(args, this, true);
		super.start(args);
		System.out.printf("\nGenerating Airdrie Transit bus data... DONE in %s.\n", Utils.getPrettyDuration(System.currentTimeMillis() - start));
	}

	@Override
	public boolean excludeCalendar(GCalendar gCalendar) {
		if (this.serviceIds != null) {
			return excludeUselessCalendar(gCalendar, this.serviceIds);
		}
		return super.excludeCalendar(gCalendar);
	}

	@Override
	public boolean excludeCalendarDate(GCalendarDate gCalendarDates) {
		if (this.serviceIds != null) {
			return excludeUselessCalendarDate(gCalendarDates, this.serviceIds);
		}
		return super.excludeCalendarDate(gCalendarDates);
	}

	@Override
	public boolean excludeTrip(GTrip gTrip) {
		if (this.serviceIds != null) {
			return excludeUselessTrip(gTrip, this.serviceIds);
		}
		return super.excludeTrip(gTrip);
	}

	@Override
	public Integer getAgencyRouteType() {
		return MAgency.ROUTE_TYPE_BUS;
	}

	private static final Pattern DIGITS = Pattern.compile("[\\d]+");

	private static final long RID_ENDS_WITH_AM = 10000l;
	private static final long RID_ENDS_WITH_PM = 20000l;

	@Override
	public long getRouteId(GRoute gRoute) {
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
				System.out.printf("\nUnexptected route ID for %s!\n", gRoute);
				System.exit(-1);
				return -1l;
			}
		}
		return Long.parseLong(gRoute.getRouteShortName());
	}

	@Override
	public String getRouteShortName(GRoute gRoute) {
		return super.getRouteShortName(gRoute);
	}

	@Override
	public boolean mergeRouteLongName(MRoute mRoute, MRoute mRouteToMerge) {
		System.out.printf("\nUnexpected routes long name to merge: %s & %s!\n", mRoute, mRouteToMerge);
		System.exit(-1);
		return false;
	}

	private static final String AGENCY_COLOR_BLUE = "0099CC"; // BLUE (from web site CSS)
	// private static final String AGENCY_COLOR_BLUE_DARK = "003399"; // BLUE DARK (from web site CSS)

	private static final String AGENCY_COLOR = AGENCY_COLOR_BLUE;

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
		HashMap<Long, RouteTripSpec> map2 = new HashMap<Long, RouteTripSpec>();
		ALL_ROUTE_TRIPS2 = map2;
	}

	@Override
	public int compareEarly(long routeId, List<MTripStop> list1, List<MTripStop> list2, MTripStop ts1, MTripStop ts2, GStop ts1GStop, GStop ts2GStop) {
		if (ALL_ROUTE_TRIPS2.containsKey(routeId)) {
			return ALL_ROUTE_TRIPS2.get(routeId).compare(routeId, list1, list2, ts1, ts2, ts1GStop, ts2GStop);
		}
		return super.compareEarly(routeId, list1, list2, ts1, ts2, ts1GStop, ts2GStop);
	}

	@Override
	public ArrayList<MTrip> splitTrip(MRoute mRoute, GTrip gTrip, GSpec gtfs) {
		if (ALL_ROUTE_TRIPS2.containsKey(mRoute.getId())) {
			return ALL_ROUTE_TRIPS2.get(mRoute.getId()).getAllTrips();
		}
		return super.splitTrip(mRoute, gTrip, gtfs);
	}

	@Override
	public Pair<Long[], Integer[]> splitTripStop(MRoute mRoute, GTrip gTrip, GTripStop gTripStop, ArrayList<MTrip> splitTrips, GSpec routeGTFS) {
		if (ALL_ROUTE_TRIPS2.containsKey(mRoute.getId())) {
			return SplitUtils.splitTripStop(mRoute, gTrip, gTripStop, routeGTFS, ALL_ROUTE_TRIPS2.get(mRoute.getId()));
		}
		return super.splitTripStop(mRoute, gTrip, gTripStop, splitTrips, routeGTFS);
	}

	@Override
	public void setTripHeadsign(MRoute mRoute, MTrip mTrip, GTrip gTrip, GSpec gtfs) {
		if (ALL_ROUTE_TRIPS2.containsKey(mRoute.getId())) {
			return; // split
		}
		if (mRoute.getId() == 1l) {
			if (gTrip.getDirectionId() == MInboundType.INBOUND.intValue()) {
				mTrip.setHeadsignString(SAGEWOOD, gTrip.getDirectionId());
				return;
			} else if (gTrip.getDirectionId() == MInboundType.OUTBOUND.intValue()) {
				mTrip.setHeadsignString(GENESIS_PLACE, gTrip.getDirectionId());
				return;
			}
		} else if (mRoute.getId() == 2l) {
			if (gTrip.getDirectionId() == MInboundType.INBOUND.intValue()) {
				mTrip.setHeadsignString(REUNION, gTrip.getDirectionId());
				return;
			} else if (gTrip.getDirectionId() == MInboundType.OUTBOUND.intValue()) {
				mTrip.setHeadsignString(SIERRA_SPRINGS, gTrip.getDirectionId());
				return;
			}
		} else if (mRoute.getId() == 3l) {
			if (gTrip.getDirectionId() == MInboundType.INBOUND.intValue()) {
				mTrip.setHeadsignString(GENESIS_PLACE, gTrip.getDirectionId());
				return;
			} else if (gTrip.getDirectionId() == MInboundType.OUTBOUND.intValue()) {
				mTrip.setHeadsignString(_8TH_STREET, gTrip.getDirectionId());
				return;
			}
		} else if (mRoute.getId() == 900l) {
			if (gTrip.getDirectionId() == MInboundType.INBOUND.intValue()) {
				mTrip.setHeadsignString(AIRDRIE, gTrip.getDirectionId());
				return;
			} else if (gTrip.getDirectionId() == MInboundType.OUTBOUND.intValue()) {
				mTrip.setHeadsignString(MC_KNIGHT, gTrip.getDirectionId());
				return;
			}
		} else if (mRoute.getId() == 901l + RID_ENDS_WITH_AM) { // 901AM
			if (gTrip.getDirectionId() == MInboundType.INBOUND.intValue()) {
				mTrip.setHeadsignString(AIRDRIE, gTrip.getDirectionId());
				return;
			} else if (gTrip.getDirectionId() == MInboundType.OUTBOUND.intValue()) {
				mTrip.setHeadsignString(CALGARY, gTrip.getDirectionId());
				return;
			}
		} else if (mRoute.getId() == 901l + RID_ENDS_WITH_PM) { // 901PM
			if (gTrip.getDirectionId() == MInboundType.INBOUND.intValue()) {
				mTrip.setHeadsignString(AIRDRIE, gTrip.getDirectionId());
				return;
			} else if (gTrip.getDirectionId() == MInboundType.OUTBOUND.intValue()) {
				mTrip.setHeadsignString(CALGARY, gTrip.getDirectionId());
				return;
			}
		} else if (mRoute.getId() == 902l + RID_ENDS_WITH_AM) { // 902AM
			if (gTrip.getDirectionId() == MInboundType.INBOUND.intValue()) {
				mTrip.setHeadsignString(AIRDRIE, gTrip.getDirectionId());
				return;
			} else if (gTrip.getDirectionId() == MInboundType.OUTBOUND.intValue()) {
				mTrip.setHeadsignString(CALGARY, gTrip.getDirectionId());
				return;
			}
		} else if (mRoute.getId() == 902l + RID_ENDS_WITH_PM) { // 902PM
			if (gTrip.getDirectionId() == MInboundType.INBOUND.intValue()) {
				mTrip.setHeadsignString(AIRDRIE, gTrip.getDirectionId());
				return;
			} else if (gTrip.getDirectionId() == MInboundType.OUTBOUND.intValue()) {
				mTrip.setHeadsignString(CALGARY, gTrip.getDirectionId());
				return;
			}
		}
		mTrip.setHeadsignString(cleanTripHeadsign(gTrip.getTripHeadsign()), gTrip.getDirectionId());
	}

	@Override
	public boolean mergeHeadsign(MTrip mTrip, MTrip mTripToMerge) {
		System.out.printf("\nUnexpected trips to merge: %s & %s!\n", mTrip, mTripToMerge);
		System.exit(-1);
		return false;
	}

	@Override
	public String cleanTripHeadsign(String tripHeadsign) {
		tripHeadsign = CleanUtils.cleanSlashes(tripHeadsign);
		tripHeadsign = CleanUtils.cleanNumbers(tripHeadsign);
		tripHeadsign = CleanUtils.cleanStreetTypes(tripHeadsign);
		return CleanUtils.cleanLabel(tripHeadsign);
	}

	private static final Pattern STARTS_WITH_BOUNDS = Pattern.compile("(^(eb|nb|sb|wb) )*", Pattern.CASE_INSENSITIVE);

	@Override
	public String cleanStopName(String gStopName) {
		gStopName = STARTS_WITH_BOUNDS.matcher(gStopName).replaceAll(StringUtils.EMPTY);
		gStopName = CleanUtils.CLEAN_AND.matcher(gStopName).replaceAll(CleanUtils.CLEAN_AND_REPLACEMENT);
		gStopName = CleanUtils.CLEAN_AT.matcher(gStopName).replaceAll(CleanUtils.CLEAN_AT_REPLACEMENT);
		gStopName = CleanUtils.cleanSlashes(gStopName);
		gStopName = CleanUtils.removePoints(gStopName);
		gStopName = CleanUtils.cleanStreetTypes(gStopName);
		return CleanUtils.cleanLabel(gStopName);
	}

	@Override
	public int getStopId(GStop gStop) {
		if (!StringUtils.isEmpty(gStop.getStopCode()) && Utils.isDigitsOnly(gStop.getStopCode())) {
			return Integer.parseInt(gStop.getStopCode()); // use stop code as stop ID
		}
		System.out.printf("\nUnexpected stop ID for %s!\n", gStop);
		System.exit(-1);
		return -1;
	}
}
