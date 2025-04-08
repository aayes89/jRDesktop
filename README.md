# jRDesktop
Remote Desktop Server for Java

Java implementation for screenshot, terminal access and keystroke handling using a web browser or console "terminal" as the interface when there is no headless environment.

Needs some improvements in handling keystrokes and combinations.
Screenshots are taken through the Robot class.
Keybindings are handled with HashMaps.
Web server publishing is done with SocketServer.
# How to use
- Clean and build with NetBeans
- in the console (terminal) run java -jar compiled.jar or directly from Netbeans for debugging purposes.
- if it is a headless environment (no UI or X11): run in terminal mode with most commands available for each OS.
