# Flows

A **flow** is a video stream which is not provided directly by a camera, but by
a server called [streambed].  Flows can be used to transcode from one video
encoding to another, overlay text, or rebroadcast a unicast RTSP stream to a
[multicast] address.

Streambed can run on one or more dedicated computers, and is controlled by IRIS
through the `streambed` [protocol].  A [controller] and associated [comm link]
must exist for each streambed server.  Each flow must be assigned to an [IO pin]
on a streambed controller.

## Configuration

Select `View ➔ Video ➔ Flows` menu item

To configure a flow, edit the first eight fields in the table.  A flow can be
either a _camera flow_ or a _video monitor flow_, but not both.

Field            | Description
-----------------|--------------------------------------------------------
Flow             | Flow name
Location overlay | Flag indicating whether camera location should be added
View num         | Fixed position view number for encoder type
Quality          | Encoder stream quality
Camera           | Camera name
Monitor number   | [Video monitor] number
Address          | Video monitor's _sink_ address
port             | Video monitor's _sink_ port

## Status

The current flow status is displayed in the last 4 fields.

Field  | Description
-------|--------------------------------
State  | `STARTING`, `PLAYING`, `FAILED`
Pushed | Pushed packet count
Lost   | Lost packet count
Late   | Late packet count

## Camera Flows

A _camera flow_ is used to rebroadcast a camera stream.  The `view num`,
`quality` and `camera` fields should be configured.  `Monitor number`, `address`
and `port` must be blank.

The camera's [encoder type] must contain two [stream]s with matching values for
`view num` and `quality`.  The `flow` field must be checked on one but not the
other.  They define the _sink_ (checked) and _source_ (unchecked) of the flow.

## Video Monitor Flows

A _video monitor flow_ can rebroadcast the stream currently being displayed on a
[video monitor].  The `view num`, `quality`, `monitor number`, `address` and
`port` fields should be configured.  `Camera` must be blank.

The _source_ is defined by the current camera displayed on the specified monitor
number.  That camera's [encoder type] must contain a [stream] with matching
`view num` and `quality`.  If multiple streams match, the stream with `flow`
checked is used.

The `address` and `port` fields define the flow's _sink_.

## Transcoding

If the _sink_ encoding is different than the _source_, the flow will be
_transcoded_ by streambed.  Warning: transcoding requires more CPU time than
simply rebroadcasting.


[comm link]: comm_links.html
[controller]: controllers.html
[IO pin]: controllers.html#io-pins
[multicast]: https://en.wikipedia.org/wiki/Multicast_address
[protocol]: comm_links.html#protocols
[streambed]: https://github.com/mnit-rtmc/streambed
[video monitor]: video.html