# jRDesktop
Remote Desktop Server for Java

Java implementation for screen capture, granting root access, and keystroke handling using a web browser or a "terminal" console as an interface when the environment does not have a UI.

Needs some improvements in handling keystrokes and combinations.<br>
Screenshots are taken through the Robot class.<br>
Keybindings are handled with HashMaps.<br>
Web server publishing is done with SocketServer.<br>
# How to use
- Clean and build with NetBeans
- in the console (terminal) run java -jar compiled.jar or directly from the IDE for debugging purposes.
- if it is a headless environment (no UI or X11): run in terminal mode with most commands available for each OS (Linux, MacOs, Windows)
