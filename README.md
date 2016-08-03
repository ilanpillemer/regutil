# GameOn! Registration Utility

A collection of utilities that allow you to manage GameOn! room registrations via the command line.

## Java utility

```
Usage : <options> <path to registration json file>

Required parameters
	-i=<gameon id>
	-s=<gameon secret>

Optional parameters
	-u=<map service URL>
	-r=<room ID>
	-m=<HTTP method, defaults to POST if not specified>
```

### Examples

Register a new room :


### Sample JSON

You can use the sample JSON file shown below as the starting point for your room registration. Simply change the values as required.

```
{
"name":"EasyReg",
"fullName":"A room registered by EasyReg tm.",
"description":"Command line registration tool for room developers.",
"doors":{
	"s":"A winding path leading off to the south",
	"d":"A tunnel, leading down into the earth",
	"e":"An overgrown road, covered in brambles",
	"u":"A spiral set of stairs, leading upward into the ceiling",
	"w":"A shiny metal door, with a bright red handle",
	"n":"A Large doorway to the north"
},
"connectionDetails":{
	"type":"websocket",
	"target":"ws://172.17.0.11:9080/rooms/myRoom"
}
}

```

### Building the utility

The utility can be built by running `gradlew build`. This will build the minimal jar needed for inclusion in an application. However, if you wish to use the utility directly formt he command line, use the command `gradlew fatjar` to include all required dependencies into the jar file. You can then run the utility as above by specifying `java -jar regutil-app/build/libs/regutil-app.jar` and adding the parameters listed above.
