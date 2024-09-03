# jRDesktop
Remote Desktop Server for Java

Java implementation for screenshot, terminal access and keystroke handling using a web browser as the interface when there is no headless environment.

Needs some improvements in handling keystrokes and combinations.
Screenshots are taken through the Robot class.
Keybindings are handled with HashMaps.
Web server publishing is done with SocketServer.
# How to use
- Compile and build with NetBeans
- in the console (terminal) run java -jar compiled.jar
- if it is a headless environment (no UI or X11): run in terminal mode with most commands available for each OS.
