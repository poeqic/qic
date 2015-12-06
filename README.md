# QIC

[![Join the chat at https://gitter.im/poeqic/qic](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/poeqic/qic?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
Quasi-In-Chat Search

QIC is trade search tool (as of 0.1) that aims to allow Path of Exile players to have an in-game items search tool. It features searching by search terms (or keywords) like "50life 60res 4L boots".

It monitors the Path of Exile client log file for commands typed in-game and uses AHK to display an On-Screen Display (OSD). Commands can be extended or modified via .txt files in keywords directory. Commands work when typed in all Chat Channels except the following: "Global", "Trade", "Whisper". This is subject to changes.

# Commands

* `search` or `s` `{search terms}` - runs a search given _search term_.
  * _Example_: `s gloves 80life 90eleres`
* `searchend` or `se` - closes the search result window.
* `reload` - reloads overlay_config.ini and the GUI.
* `searchexit` or `sexit` - stops QIC. You'll need to run _qic.ahk_ again to execute commands.
 
_Overlay needs to be activated/visible for the following commands to work._
* `0`,`1`,`2`..`n` - generate WTB message for item `#n` and paste to chat (You have to send it yourself).
* `swtb{n}` - Saves WTB message `#n` to `savedWTB_messages.txt`.
  * _Example_: `setps5`
* `who{n}` - sends /whois for seller of item `#n`.
  * _Example_: `who4`
* `sort*` - sort current results (see keywords/sort.txt).
  * _Example_: `sortlife`
* `view{n}` - view all stats for item `#n`.
  * _Example_: `view11`
* `page{n}` - jump to search result page `#n`.
  * _Example_: `page3`
* `listleagues` - Displays a List of all available Leagues you can search in.
* `setleague{n}` - Writes League `#n` to config file.
  * _Example_: `setleague2`
* `setps{n}` - Writes PageSize `#n` to config file.
  * _Example_: `setps5`

# Shortcuts
All shortcut can be configured in qic.ahk.
* `ctrl+q` - Toggle the GUI ON/OFF.
* `PageUp` / `PageDown` - Browse through search results.

# Screenshots

![6](https://dl.dropboxusercontent.com/u/13620316/wts-screen01.png)

![7](https://dl.dropboxusercontent.com/u/13620316/wts-screen02.png)

# How to Install/Run

To run QIC, you'll need:

1. Java installed. Go to https://www.java.com/ to download the latest version of Java.
2. AHK installed. Go to http://ahkscript.org/ to download the latest version of AutoHotkey.
3. (Not required) Configure your overlay_config.ini file:
  * PoEClientLogFile - path to your Path of Exile Client.txt directory.  
    QIC reads the path from Windows registry, only needs to be set if this should fail.
4. (Not required) Install the Path of Exile font "Fontin" for a better experience, located in subfolder "resource".
5. Run via qic.ahk  

If you have any problems, enable the debug mode (see Config Options below).

# Noteworthy Config Options

`Config file = overlay_config.txt`, every option has a default value if no value is specified.

* `SearchLeague ="tempstandard"`  
; Possible values:   
; 	`"tempstandard"` (current SC Temp-League)   
;	`"temphardcore"` (current HC Temp-League)   
;	`"standard"`, `"hardcore"`  

* `DebugMode = 1`  
; `default = 0` (`1: true`, `0: false`)  
; `writes debug info to debug_log.txt`

* `AbsolutePositionLeft =`  
; `default = 2/3 of WindowWidth` (don't set if you play in bordered windowed mode)  
* `AbsolutePositionTop =`  
; `default = 5` (don't set if you play in bordered windowed mode)  

# Contributors

/u/Eruyome87 - AHK icons and AHK development

---

QIC is 100% free and open source licensed under GPLv2
Created by: /u/ProFalseIdol IGN: ManicCompression
QIC is fan made tool and is not affiliated with Grinding Gear Games in any way.
