# Deploy command

Add an executable repository-root script named `deploy`.

`./deploy` builds the debug APK, installs it on the default Philips target
`192.168.0.200:5555`, stops the app, and launches it. `./deploy <serial>` uses
the supplied ADB serial instead. The script exits on the first failed command,
so an install or launch never follows a failed build.
