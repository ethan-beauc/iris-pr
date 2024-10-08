# Parking Areas

The number of available spaces in a parking area can be published on the web,
or displayed on a DMS.  A parking area could be a rest area or a parking garage.
The honeybee server can publish parking area information compatible with the
MAASTO truck parking information management system (TPIMS).

A special [corridor] must be associated with the parking area in order to allow
counting available spaces.  Within that corridor, detectors with the **parking**
lane type will be used.

| Lane Number | Parking Space |
|-------------|---------------|
| 1           | Head (front)  |
| 2           | Head (rear)   |
| 3           | Tail (front)  |
| 4           | Tail (rear)   |

## Parking Area Action Tag

The number of available parking spaces can be displayed in DMS messages using
[device actions].  A `[pa` *…* `]` [action tag] in the [message pattern] will
be replaced with the appropriate value.  It has the following format:

`[pa` *id,low,closed* `]`

**Parameters**

1. `id`: Parking area ID
2. `low`: Text to display if available spaces is below the low threshold
3. `closed`: Text to display if parking area is closed

### Example

```
REST AREA 2 MILES[nl]PARKING [pa1,LOW,CLOSED]
```


[action tag]: action_plans.html#action-tags
[corridor]: road_topology.html#corridors
[device actions]: action_plans.html#device-actions
[message pattern]: message_patterns.html
