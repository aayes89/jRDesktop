# jRDesktop
Remote Desktop Server for Java

Java implementation for screenshot, terminal access and keystroke handling using a web browser or console "terminal" as the interface when there is no headless environment.

Needs some improvements in handling keystrokes and combinations.<br>
Screenshots are taken through the Robot class.<br>
Keybindings are handled with HashMaps.<br>
Web server publishing is done with SocketServer.<br>
# How to use
- Clean and build with NetBeans
- in the console (terminal) run java -jar compiled.jar or directly from the IDE for debugging purposes.
- if it is a headless environment (no UI or X11): run in terminal mode with most commands available for each OS.
