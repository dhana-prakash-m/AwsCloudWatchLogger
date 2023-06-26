package com.dhanaprakash.awslogger

import com.amazonaws.regions.Regions

/**
 * The enum class representing the different aws regions
 *
 * @param awsRegion The aws region
 */
enum class DLRegions(val awsRegion: Regions) {
    US_EAST_1(Regions.US_EAST_1),
    US_EAST_2(Regions.US_EAST_2),
    AP_SOUTH_1(Regions.AP_SOUTH_1),
    AP_SOUTHEAST_1(Regions.AP_SOUTHEAST_1),
    AP_SOUTHEAST_2(Regions.AP_SOUTHEAST_2),
    AP_SOUTHEAST_3(Regions.AP_SOUTHEAST_3),
    EU_SOUTH_1(Regions.EU_SOUTH_1),
    EU_NORTH_1(Regions.EU_NORTH_1),
    AP_EAST_1(Regions.AP_EAST_1),
    AP_NORTHEAST_1(Regions.AP_NORTHEAST_1),
    AP_NORTHEAST_2(Regions.AP_NORTHEAST_2),
    SA_EAST_1(Regions.SA_EAST_1),
    CN_NORTH_1(Regions.CN_NORTH_1),
    CN_NORTHWEST_1(Regions.CN_NORTHWEST_1),
    ME_SOUTH_1(Regions.ME_SOUTH_1),
    AF_SOUTH_1(Regions.AF_SOUTH_1),
    EU_WEST_1(Regions.EU_WEST_1),
    EU_WEST_2(Regions.EU_WEST_2),
    EU_WEST_3(Regions.EU_WEST_3),
    US_WEST_1(Regions.US_WEST_1),
    US_WEST_2(Regions.US_WEST_2),
    EU_CENTRAL_1(Regions.EU_CENTRAL_1),
    CA_CENTRAL_1(Regions.CA_CENTRAL_1),
}