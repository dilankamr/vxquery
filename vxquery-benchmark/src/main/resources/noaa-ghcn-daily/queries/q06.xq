(: XQuery Self Join Query :)
(: Self join with all stations finding the difference in min and max       :)
(: temperature.                                                            :)
let $sensor_collection_min := "/tmp/1.0_partition_ghcnd_all_xml/sensors"
for $r_min in collection($sensor_collection_min)/dataCollection/data

let $sensor_collection_max := "/tmp/1.0_partition_ghcnd_all_xml/sensors"
for $r_max in collection($sensor_collection_max)/dataCollection/data

where $r_min/station eq $r_max/station
    and $r_min/date eq $r_max/date
    and $r_min/dataType eq "TMIN"
    and $r_max/dataType eq "TMAX"
return ($r_max/value, $r_min/value, $r_max/value - $r_min/value)