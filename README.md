# suspender
It allows suspending arbitrary objects for a certain amount of time.
After the time expires all the listeners subscribed to the object restoration event will be notified.
You can always restore the object explicitly using ```suspender.restore(Path)``` before the time expires. 

Each object is suspended by a certain unique path (```net.devromik.suspender.Path```).
A path has the following format: ```/segment_1/segment_2/.../segment_N.```
A path must have at least two segments.
Paths are intended for performing hierarchical grouping of suspended objects.
Grouping allows you to work with the suspended objects on a group level.
For example, it is possible to explicitly restore a whole group of suspended objects.

Use the ```suspender.hasObjectsSuspendedBy(Path)``` method to check if there are any objects
suspended by a path with the ```'/A/B/C'``` prefix (for example: ```'/A/B/C'```, ```'/A/B/C/D/E'```).
To restore all these objects, use the method ```suspender.restore(Path)```.

You can restore a suspended object with the closest restoration time using ```suspender.restoreObjectWithMinRestorationTime(Path)```.

You can get notifications on object restoration by registering a listener: ```addRestoredObjectListener(RestoredObjectListener)```.

Call the ```suspender.start()``` method before working with a suspender.
Call the ```suspender.stop()``` method after working with a suspender.

For additional information please see ```net.devromik.suspender.Suspender```.
Also, there are many unit tests which provide examples of how to use the library. For example, ```MemSuspenderTest```.

# License

MIT
