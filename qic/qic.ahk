; QIC (Quasi-In-Chat) Search
;
; Written by:
; /u/Eruyome87 
; /u/ProFalseIdol
;
; Latest Version will always be at:
; https://github.com/thirdy/wts/
;
; Feel free to make pull-requests.
;

IfWinActive, Path of Exile ahk_class Direct3DWindowClass
#SingleInstance force ; If it is alReady Running it will be restarted.
#NoEnv  ; Recommended for performance and compatibility with future AutoHotkey releases.
#Persistent ; Stay open in background
SendMode Input  ; Recommended for new scripts due to its superior speed and reliability.
SetWorkingDir %A_ScriptDir%  ; Ensures a consistent starting directory.
SetBatchLines, -1

#Include, lib/Gdip_All.ahk
; https://www.autohotkey.com/boards/viewtopic.php?t=1879
#Include, lib/Gdip_Ext.ahk
; https://github.com/cocobelgica/AutoHotkey-JSON
#Include, lib/JSON.ahk

Menu, tray, Tip, Path of Exile - QIC (Quasi-In-Chat) Search
Menu, tray, Icon, resource/qic$.ico

If (A_AhkVersion <= "1.1.22"){
    msgbox, You need AutoHotkey v1.1.22 or later to run this script. `n`nPlease go to http://ahkscript.org/download and download a recent version.
    Exit
}

; Set Hotkey for toggling GUI overlay completely OFF, default = ctrl + q
; ^p and ^i conflicts with trackpetes ItemPriceCheck macro
Hotkey, ^q, ToggleGUI
; Set Hotkeys for browsing through search results
Hotkey, PgUp, PreviousPage
Hotkey, PgDn, NextPage

; Start gdi+
If !pToken := Gdip_Startup()
{
   MsgBox, 48, gdiplus error!, Gdiplus failed to start. Please ensure you have gdiplus on your system
   ExitApp
}
OnExit, Exit

StringReplace, param1, parm1, $LF, `n, All
StringReplace, param2, parm2, $LF, `n, All
global poeWindowName = "Path of Exile ahk_class Direct3DWindowClass"
global poeWinID := WinExist(poeWindowName)
global isFullScreen := isWindowedFullScreen(poeWinID)
global debugActive := 
global PageSize := 5
global PageNumbers := 0
global ResultPages := []
global SearchResults := []
global SearchResultsWTB := []
global LastSelectedPage := 1
global TextToDraw = ""
global selectedFileDirectory := ReadValueFromIni("PoEClientLogPath", , "System")
global selectedFile := selectedFileDirectory "\Client.txt"
global iniFilePath := "overlay_config.ini"
global Leagues := []
	Leagues.Insert(e:=["tmpstandard","Current Temp-SC League"])
	Leagues.Insert(e:=["tmphardcore","Current Temp-HC League"])
	Leagues.Insert(e:=["standard","Standard League"])
	Leagues.Insert(e:=["hardcore","Hardcore League"])	
global PlayerList := [] ; array of strings
global searchTermPrefix := 
global searchTerm := 
global lastSearch := 
global ItemResults =
global useSimpleText := 0
global poeWindowXpos :=
global poeWindowYpos :=
global poeWindowWidth :=
global poeWindowHeight :=
global GuiON = 1
lastTimeStamp := 0

Gosub, ReadIniValues

FileRead, BIGFILE, %selectedFile%
StringGetPos, charCount, BIGFILE,`n, R2 ; Init charCount to the location of the 2nd last location of `n. Note that Client.txt always has a trailing newline

;;; DEBUG
debug := "--------------------------------------------------------------------------------------------------"
WriteDebugLog(debug)
debug := "Started script."
WriteDebugLog(debug)
debug := "AHK version: " A_AhkVersion " " (A_PtrSize = 4 ? 32 : 64) "-bit " (A_IsUnicode ? "Unicode" : "ANSI")
WriteDebugLog(debug)
debug := isFullScreen ? "Game is in Windowed Fullscreen Mode." : "Game is in Windowed Mode."
WriteDebugLog(debug)
;;;

;;; DEBUG
debug := "Path Of Exile Window: Xpos=" poeWindowXpos ", Ypos=" poeWindowYpos ", Width=" poeWindowWidth ", Height=" poeWindowHeight
WriteDebugLog(debug)
debug := "Overlay DrawingArea: Xpos=" DrawingAreaPosX ", Ypos=" DrawingAreaPosY ", Width=" DrawingAreaWidth ", Height=" DrawingAreaHeight
WriteDebugLog(debug)
;;;

Font := CheckFont("Arial")

; Extra options:
; ow4         - Sets the outline width to 4
; ocFF000000  - Sets the outline colour to opaque black
; OF1			- If this option is set to 1 the text fill will be drawn using the same path that the outline is drawn.
AHKArchitecture := (A_PtrSize = 4 ? 32 : 64)
AHKEncoding := (A_IsUnicode ? "Unicode" : "ANSI")
If ((AHKEncoding != "Unicode") && (AHKArchitecture = 32) || (AHKArchitecture = 32)) {
	Options = x5 y5 w%tWidth% h%tHeight% Left cffffffff r4 s%FontSize%
	useSimpleText := 1
	;;; DEBUG	
	debug := "Using Text without Outline."
	WriteDebugLog(debug)
	;;;
}
Else {
	Options = x5 y5 w%tWidth% h%tHeight% Left cffffffff ow2 ocFF000000 OF1 r4 s%FontSize%
	useSimpleText := 0
	;;; DEBUG	
	debug := "Using Text with Outline."
	WriteDebugLog(debug)
	;;;
}

Gui, 1:  -Caption +E0x80000 +LastFound +OwnDialogs +Owner +AlwaysOnTop

hwnd1 := WinExist()
hbm := CreateDIBSection(DrawingAreaWidth, DrawingAreaHeight)
hdc := CreateCompatibleDC()
obm := SelectObject(hdc, hbm)
G := Gdip_GraphicsFromHDC(hdc)
Gdip_SetSmoothingMode(G, 4)

Gosub, DrawOverlay

OnMessage(0x201, "WM_LBUTTONDOWN")

Gosub, CheckWinActivePOE
SetTimer, CheckWinActivePOE, 100
Gosub, WatchInput
SetTimer, WatchInput, 100

Return


WM_LBUTTONDOWN() {
   PostMessage, 0xA1, 2
}

; ------------------ GET AND SET (UPDATE) DIMENSIONS AND POSITIONS------------------ 
; https://github.com/tariqporter/Gdip/blob/master/Gdip.Tutorial.8-Write.text.onto.a.gui.ahk
; Set the width and height we want as our drawing area, to draw everything in. This will be the dimensions of our bitmap
GetAndSetDimensions:
	WinGetPos, poeWindowXpos, poeWindowYpos, poeWindowWidth, poeWindowHeight, %poeWindowName%
	If !isFullScreen {
		; windowed mode
		SysGet, windowTitlebarHeight, 31
		windowTitlebarHeight := windowTitlebarHeight + 8
	}
	Else {
		; fullscreen borderless
		windowTitlebarHeight := 0
	}
	
	DrawingAreaWidth 	:= ReadValueFromIni("Width", 310)
	DrawingAreaPosX 	:= ReadValueFromIni("AbsolutePositionLeft", ceil(poeWindowXpos + poeWindowWidth * 0.33 + DrawingAreaWidth))
	DrawingAreaPosY 	:= ReadValueFromIni("AbsolutePositionTop", poeWindowYpos + windowTitlebarHeight + 5)
	DrawingAreaHeight	:= ReadValueFromIni("Height", (poeWindowHeight - windowTitlebarHeight - 50))
	FontSize 			:= ReadValueFromIni("FontSize", 13)
	PageSize 			:= ReadValueFromIni("PageSize", 5)
	tWidth := DrawingAreaWidth - 8
	tHeight := DrawingAreaHeight - 8	
Return

; ------------------ READ ALL OTHER INI VALUES ------------------ 
ReadIniValues:
	Gosub, GetAndSetDimensions
	debugActive := ReadValueFromIni("DebugMode", 0 , "System")
	selectedFileDirectory := ReadValueFromIni("PoEClientLogPath", , "System")
	selectedFile := selectedFileDirectory "\Client.txt"
	searchLeague := ReadValueFromIni("SearchLeague", , "Search")
	searchTermPrefix := ReadValueFromIni("SearchTermPrefix", , "Search") " " searchLeague " " 	
return

; ------------------ TOGGLE GUI ------------------ 
ToggleGUI:
	ToggleGUI()
Return

ToggleGUI(){
	If (GuiON = 0) {
		Gosub, CheckWinActivePOE
		SetTimer, CheckWinActivePOE, 100
		GuiON = 1
	}
	Else {
		SetTimer, CheckWinActivePOE, Off      
		Gui, 1: Hide	
		GuiON = 0
	}
}

; ------------------ SHOW NEXT PAGE ------------------ 
NextPage:	
	If LastSelectedPage < %PageNumbers%
		LastSelectedPage += 1
	Else
		Return
	
	Draw(ResultPages[LastSelectedPage])
Return

; ------------------ SHOW PREVIOUS PAGE ------------------ 
PreviousPage:
	If LastSelectedPage > 1
		LastSelectedPage -= 1
	Else
		Return
	
	Draw(ResultPages[LastSelectedPage])	
Return

; ------------------ Draw TEXT TO OVERLAY ------------------ 
DrawText:
	Gui, 1: Show, NA
	If (useSimpleText = 0) {
		Gdip_TextToGraphicsOutline(G, TextToDraw, Options, Font, DrawingAreaWidth, DrawingAreaHeight)
	}
	Else {
		Gdip_TextToGraphics(G, TextToDraw, Options, Font, DrawingAreaWidth, DrawingAreaHeight)
	}
	UpdateLayeredWindow(hwnd1, hdc, DrawingAreaPosX, DrawingAreaPosY, DrawingAreaWidth, DrawingAreaHeight)
	;;; DEBUG	
	debug := "Text drawn to overlay."
	WriteDebugLog(debug)
	;;;
Return

; ------------------ DRAW (REDRAW) OVERLAY ------------------ 
; https://github.com/tariqporter/Gdip/blob/master/Gdip.Tutorial.8-Write.text.onto.a.gui.ahk
; Set the width and height we want as our drawing area, to draw everything in. This will be the dimensions of our bitmap
DrawOverlay:
	Gdip_GraphicsClear(G)
	pBrush := Gdip_BrushCreateSolid(0xffb4804b)
	; left border
	Gdip_FillRectangle(G, pBrush, 0, 0, 1, DrawingAreaHeight)
	; right border
	Gdip_FillRectangle(G, pBrush, DrawingAreaWidth - 2, 0, 1, DrawingAreaHeight)
	; top border
	Gdip_FillRectangle(G, pBrush, 0, 1, DrawingAreaWidth, 1)
	; bottom border
	Gdip_FillRectangle(G, pBrush, 0, DrawingAreaHeight - 2, DrawingAreaWidth, 1)
	; background
	pBrush := Gdip_BrushCreateSolid(0x47000000)
	Gdip_FillRectangle(G, pBrush, 0, 0, DrawingAreaWidth, DrawingAreaHeight)
Return

; ------------------ CALL DRAW SUBROUTINES ------------------ 
Draw(text=""){
	If !isFullScreen {
		Gosub, GetAndSetDimensions
	}
	Gosub, DrawOverlay	
	TextToDraw := text
	Gosub, DrawText	
}

; ------------------ READ INI AND CHECK IF VARIABLES ARE SET ------------------ 
ReadValueFromIni(IniKey, DefaultValue = "", Section = "Overlay"){
	IniRead, OutputVar, %iniFilePath%, %Section%, %IniKey%
	If !OutputVar
		OutputVar := DefaultValue
	Return OutputVar
}

; ------------------ WRITE TO INI ------------------
WriteValueToIni(IniKey,NewValue,IniSection){
	IniWrite, %NewValue%, %iniFilePath%, %IniSection%, %IniKey%
	Gosub, ReadIniValues
}

; ------------------ READ FONT FROM INI AND CHECK IF INSTALLED ------------------
CheckFont(DefaultFont){
	; Next we can check that the user actually has the font that we wish them to use
	; If they do not then we can do something about it. I choose to default to Arial.
	IniRead, InputFont, %iniFilePath%, Overlay, FontFamily
	If !hFamily := Gdip_FontFamilyCreate(InputFont)	{
	   OutputFont := DefaultFont
	}
	Else {
		Gdip_DeleteFontFamily(hFamily)
		OutputFont := InputFont
	}
	;;; DEBUG	
	debug := "Using font: " OutputFont
	WriteDebugLog(debug)
	;;;
	Return OutputFont
}

; ------------------ PAGE SEARCH RESULTS ------------------
PageSearchResults:
	LastSelectedPage := 1
	Temp := ItemObjectsToString(ItemResults)
	SearchResults := Temp[1]
	SearchResultsWTB := Temp[2]
	PageNumbers := ceil(SearchResults.MaxIndex() / PageSize)
	ResultPages := []
	
	If PageNumbers = 0
		PageNumbers := 1
	
	LastIndex = 0
	Loop %PageNumbers%
	{	
		If !parsedJSON.league
			league := "League Placeholder"
		Page := league " | Page " A_Index "/" PageNumbers " " "`r`n"
		Loop %PageSize%
		{
			Page .= SearchResults[A_Index+LastIndex]
		}
		If !SearchResults[1] {
			Page .= "_______________________________________________" "`r`n" "`r`n"
			Page .= "0 search results."
		}
		LastIndex := PageSize * A_Index
		ResultPages.Insert(Page)
	}
	
	;;; DEBUG	
	debug := "Search results paged."
	WriteDebugLog(debug)
	;;;
	Draw(ResultPages[LastSelectedPage])
Return

; ------------------ RETURN PRINTABLE ITEMS ------------------ 
ItemObjectsToString(ObjectArray){
	oa := ObjectArray
	o := []
	d := []
	s := 	
	smallSeperator := "-----------"
	bigSeperator := "_______________________________________________"
	; !!!!!!!!!!!!!!!! Gem/Map Level, Quantity Stack !!!!!!!!!!!!!!!!
	
	for i, e in oa {
		su =
		wtb = 
		; Add item index, name, sockets and quality			
		su .= bigSeperator "`r`n"
		su .= "[" e.id "] " e.name
		wtb .= "@" e.ign " Hi, I would like to buy your " e.name " listed for """ StringToUpper(e.buyout) """ in " e.league " with the following Stats:"
		If e.socketsRaw {
			su .= " " e.socketsRaw 
			wtb .= " Sockets " e.socketsRaw
		}
		If e.quality {
			su .= " " Floor(e.quality) "%"
			wtb .= " Q" Floor(e.quality) "%"
		}
		
		; Add implicit mod
		If e.implicitMod {
			su .= "`r`n"
			temp := RegExReplace(e.implicitMod.name, "#|\$",,,1)
			temp := StrReplace(temp, "#", Floor(e.implicitMod.value))
			su .= temp
			wtb .= " --- " temp
		}
		
		; Add explicit mods
		If (e.explicitMods.MaxIndex() > 0 || e.identified = 0) {
			su .=  "`r`n" smallSeperator
		}
		If e.explicitMods.MaxIndex() > 0 {
			
			for j, f in e.explicitMods {
				temp := StrReplace(f.name, "#",,,1)
				; Handle div cards
				temp2 := 
				While RegExMatch(temp, "(\{.*?\})", match) {
					temp := RegExReplace(temp, "(\{.*?\})",,,1)
					temp2 .= RegExReplace(match, "\{|\}") " "
				}
				If temp2 {
					temp := temp2
				}
				; Insert value into name
				If (f.value > 0){
					temp := StrReplace(temp, "#", Floor(f.value))
				}				
				su .= "`r`n" temp
				wtb .= " --- " temp
			}
		}	
		; Unidentified Tag
		If e.identified = 0 {
			su .= "`r`n" "Unidentified"
		}
		su .= "`r`n" smallSeperator "`r`n"
				
		; Corrupted Tag
		If e.corrupted = 1 {
			su .= "Corrupted" "`r`n"
			wtb .= " --- Corrupted" 
		}
		
		; Add defenses
		If e.armourAtMaxQuality || e.energyShieldAtMaxQuality || e.evasionAtMaxQuality || e.block {
			defenseFound := 1
			If e.armourAtMaxQuality && e.energyShieldAtMaxQuality { 
				temp := "AR: " Floor(e.armourAtMaxQuality) " " "ES: " Floor(e.energyShieldAtMaxQuality)				
			}
			Else If e.armourAtMaxQuality && e.evasionAtMaxQuality {
				temp := "AR: " Floor(e.armourAtMaxQuality) " " "EV: " Floor(e.evasionAtMaxQuality)
			}
			Else If e.evasionAtMaxQuality && e.energyShieldAtMaxQuality {
				temp := "EV: " Floor(e.evasionAtMaxQuality) " " "ES: " Floor(e.energyShieldAtMaxQuality)
			}
			Else If e.armourAtMaxQuality  {
				temp := "AR: " Floor(e.armourAtMaxQuality)
			}
			Else If e.evasionAtMaxQuality  {
				temp := "EV: " Floor(e.evasionAtMaxQuality)
			}
			Else If e.energyShieldAtMaxQuality  {
				temp := "ES: " Floor(e.energyShieldAtMaxQuality)
			}
			Else If e.armourAtMaxQuality && e.evasionAtMaxQuality && e.energyShieldAtMaxQuality {
				temp := "AR: " Floor(e.armourAtMaxQuality) " " "EV: " Floor(e.evasionAtMaxQuality) " " "ES: " Floor(e.energyShieldAtMaxQuality)
			}
			su .= temp
			wtb .= " --- @MaxQuality " temp 
			If e.block {
				su .= " Block: " Floor(e.block)
				wtb .= " Block: " Floor(e.block)
			}
		}
		
		; Add pdps, edps, aps and critchance
		If e.physDmgAtMaxQuality || e.eleDmg || e.attackSpeed || e.crit {
			damageFound := 1
			If e.physDmgAtMaxQuality {
				temp := "pDPS " e.physDmgAtMaxQuality " "
			}
			If e.eleDmg {
				temp := "eDPS " e.eleDmg " "			
			}
			If e.attackSpeed {
				temp := "APS " e.attackSpeed " "
			}
			If e.crit {
				temp := "CC " e.crit
			}
			su .= temp
			wtb .= " --- @MaxQuality " temp
		}
	
		; Add requirements
		If e.reqLvl || e.reqStr || e.reqInt || e.reqDex {
			requirementsFound := 1
			If defenseFound || damageFound {
				su .= " | "
			}
			If e.reqLvl {
				su .= "reqLvl " e.reqLvl " "
			} 
			If e.reqStr {
				su .= "Str " e.reqStr " "
			}
			If e.reqInt {
				su .= "Int " e.reqInt " "
			}
			If e.reqDex {
				su .= "Dex " e.reqDex
			}
		}
		If (defenseFound || damageFound || requirementsFound) {
			su .= "`r`n"
		}		
		
		; Add price, ign
		su .= e.buyout " " "IGN: " e.ign "`r`n"
		o[i] := su
		d[i] := wtb
	}

	temp := [o, d]
	;;; DEBUG	
	debug := "Item print view created."
	WriteDebugLog(debug)
	;;;
	return temp
}

; ---------- STRING TO UPPER AS FUNCTION -----------------
StringToUpper(s){
	StringUpper, s, s
	Return s
}

; ---------- VIEW SINGLE ITEM -----------------
ShowDetailedItem(index){
	If !parsedJSON.league
		league := "League Placeholder"
	View := league " | Detailed Item View" "`r`n" SearchResults[index+1]
	LastSelectedPage := Floor((index+1) / PageSize)
	
	Draw(View)
}

; ---------- TEST (REMOVE ME) -----------------
^!m::
  testQuery:= "s chest"
  processLine(testQuery)
Return

; ------------------ HIDE/SHOW OVERLAY IF GAME IS NOT ACTIVE/ACTIVE ------------------
CheckWinActivePOE: 
	GuiControlGet, focused_control, focus
	if(WinActive(poeWindowName))
		If (GuiON = 0) {
			Gui, 1: Show, NA
			GuiON := 1
		}
	if(!WinActive(poeWindowName))
		;If !focused_control
		If (GuiON = 1)
		{
			Gui, 1: Hide
			GuiON = 0
		}
Return

; ------------------ WATCH CLIENT.TXT ------------------ 
WatchInput:
	;StartTime := A_TickCount
	FileRead, BIGFILE, %selectedFile%
	StringGetPos, lastNewLineLocation, BIGFILE,`n, R2 ; Client.txt always has a trailing newline
	StringTrimLeft, SmallFile, BIGFILE, %lastNewLineLocation%	
	;parsedLines := ParseLines(SmallFile)		
	;ElapsedTime := A_TickCount - StartTime
	
	;MsgBox,  %ElapsedTime% milliseconds have elapsed. Output is: `r`n %SmallFile% `r`n `r`n Characters: %lastNewLineLocation%	
	; Do nothing if character count unchanged	
	If (lastNewLineLocation > charCount) {
		parsedLines := ParseLines(SmallFile)
		s := parsedLines[parsedLines.MaxIndex()].message
		charCount := lastNewLineLocation
		ProcessLine(s)
	}
Return

; ------------------ PARSE CLIENT.TXT LINES ------------------ 
ParseLines(s){
	o := []
	Loop, Parse, s, `n
	{		
		If A_LoopField {
			line := {}
			RegExMatch(s, "(\d{4}/\d{1,2}/\d{1,2} \d{1,2}:\d{1,2}:\d{1,2})", timestamp)
			; Prepare timestamp for easier comparing
			line.timestamp := RegExReplace(timestamp, "[: \/]")			
			
			StringGetPos, pos1, A_LoopField, ]
			StringTrimLeft, message, A_LoopField, pos1 + 1			
			
			StringGetPos, pos1, message, :		
			If !ErrorLevel {
				StringLeft, messagePrefix, message, pos1
				; Exclude Global, Trade and Whisper messages
				RegExMatch(messagePrefix, "[#$@]", excludedChannels)
				
				Loop % PlayerList.Length() {				
					validPlayer := InStr(messagePrefix, PlayerList[A_Index])
					If validPlayer > 0
						Break
					Else 
						validPlayer :=
				}
				
				validPlayer := 1 ; placeholder variable, remove later when playernames can be validated
				If !excludedChannels && validPlayer {
					StringTrimLeft, message, message, pos1 + 2
					StringReplace,message,message,`n,,A
					StringReplace,message,message,`r,,A
					line.message := message
					o.Insert(line)
				}				
			}			
		}		
	}		
	Return o
}

; ------------------ SEND SEARCH REQUEST, PARSE JSON ------------------ 
GetResults(term, addition = ""){
	searchTerm := """" . searchTermPrefix term " " addition . """"
	lastSearch := term
	RunWait, java -Dfile.encoding=UTF-8 -jar qic-0.2.jar %searchTerm%, , Hide ; after this line finishes, results.json should appear
	FileRead, JSONFile, results.json	
	parsedJSON 	:= JSON.Load(JSONFile, Func("reviver"))
	
	ItemResults 	:= parsedJSON.itemResults
	;;; DEBUG	
	debug := "JSON parsed."
	WriteDebugLog(debug)
	;;;
	Gosub, PageSearchResults		
}


reviver(this, key, value)
{
	;value := StrPutVar(value,var,"utf-8")
	
	return value
	;return StrPutVar([value][1], %var%, "utf-8")
}
	
StrPutVar(string, ByRef var, encoding)
{
    ; Ensure capacity.
    VarSetCapacity( var, StrPut(string, encoding)
        ; StrPut returns char count, but VarSetCapacity needs bytes.
        * ((encoding="utf-16"||encoding="cp1200") ? 2 : 1) )
    ; Copy or convert the string.
    return StrPut(string, &var, encoding),VarsetCapacity(var,-1)
}

; ------------------ GET AND PASTE WTB-MESSAGE (OR SAVE IT TO FILE) ------------------ 
GetWTBMessage(index, prepareSending){
	index := index + 1
	
	If prepareSending {
		clipboard := SearchResultsWTB[index]
		SendEvent {Enter}
		SendInput ^a
		SendInput ^v
		SendEvent {Home}
	}
	Else {
		message := SearchResultsWTB[index] 
		FormatTime, TimeString, T12, Time
		wtb := "----------------------------------------------------------------------------------" "`r`n"
		;wtb .= [%A_YYYY%/%A_MM%/%A_DD% %TimeString%]
		wtb .= "[" A_YYYY "/" A_MM "/" A_DD " " TimeString "]"
		wtb .= "`r`n" message "`r`n`r`n"
		FileAppend, %wtb%, savedWTB_messages.txt
	}	
}

; ------------------ WHO IS SELLER ------------------ 
WhoIsSeller(index){	
	index := index + 1
	s := "/whois " ItemResults[index].ign
	SendEvent {Enter}
	SendInput ^a
	SendInput %s%
	SendInput {Enter}
}

; ------------------ LIST LEAGUES ------------------ 
ListLeagues(){
	temp := "Options`r`n"
	temp .= "Type: selleague# to select a League." "`r`n"
	temp .= "_______________________________________________" "`r`n`r`n"
	for i, e in Leagues {
		temp .= i ". " e[2] "`r`n"
	}
	
	Draw(temp)
}

OpenExternalHelpFile(){
	RunWait, help.htm
}

; ------------------ PROCESS PARSED CLIENT.TXT LINE ------------------ 
ProcessLine(input){
	Length := StrLen(input)
	
	If StartsWith(input, "^s ") {
		term := StrReplace(input, "s ",,,1)
		GetResults(term)
	}
	Else If StartsWith(input, "^search ") {
		term := StrReplace(input, "search ",,,1)
		GetResults(term)
	}	
	Else If StartsWith(input, "^searchexit$") || StartsWith(input, "^sexit$") {
		Gosub, Exit
	}	
	Else If (GuiOn = 1) {
		; match "sort{sortby} (optional:asc or desc)" without tailing string, example: "sortlife" and "sortlife asc" but not "sortlife d" 
		If StartsWith(input, "^sort[a-zA-Z]+\s?(asc|desc)?$") {
			GetResults(lastSearch, input)			
		}
		; Match digits without characters after (generate and paste WTB message for item #0-98)
		Else If StartsWith(input, "^\d{1,2}$") {
			GetWTBMessage(input, 1)
		}
		; view item details
		Else If StartsWith(input, "^view\d{1,2}$") {
			Item := RegExReplace(input, "view")	
			ShowDetailedItem(Item)
		}
		; jump to page#
		Else If StartsWith(input, "^page\d{1,2}$") {
			Page := RegExReplace(input, "page")			
			If (Page > PageNumbers) {
				LastSelectedPage := PageNumbers
			}
			Else {
				LastSelectedPage := Page
			}
			Draw(ResultPages[LastSelectedPage])
		}
		; exit search
		Else If StartsWith(input, "^se$") || StartsWith(input, "^searchend$") {
			ToggleGUI()			
		}
		; prepares /whois seller message
		Else If StartsWith(input, "^who\d{1,2}$") {
			Who := RegExReplace(input, "who")	
			WhoIsSeller(Who)
		}
		; write pagesize to ini
		Else If StartsWith(input, "^setps\d{1}$") {
			option := RegExReplace(input, "setps")	
			WriteValueToIni("PageSize",option,"Overlay")
		}
		; reload overlay_config.ini
		Else If StartsWith(input, "^reload$") {
			Gosub, ReadIniValues			
			Gosub, PageSearchResults
		}
		Else If StartsWith(input, "^listleagues") {
			ListLeagues()
		}	
		Else If StartsWith(input, "^setleague[1-4]$") {
			option := RegExReplace(input, "setleague")
			option := """" Leagues[option][1] """"
			WriteValueToIni("SearchLeague",option,"Search")
			Gosub, ReadIniValues
		}
		Else If StartsWith(input, "^shelp") {
			OpenExternalHelpFile()
		}
		Else If StartsWith(input, "^swtb\d{1,2}") {
			Item := RegExReplace(input, "swtb")
			GetWTBMessage(Item, 0)
		}
	}
}

; ------------------  ------------------ 
StartsWith(s, regex){
	pos := RegExMatch(s, regex)
	If pos = 1
		Return true
	Else 
		Return false
}

; ------------------ WRITE TO DEBUG LOG ------------------ 
WriteDebugLog(debugText){
	If !debugActive {
		Return
	}
	
	FormatTime, TimeString, T12, Time
	stamp = [%A_YYYY%/%A_MM%/%A_DD% %TimeString%]
	stamp .= " " debugText "`r`n"
	FileAppend, %stamp%, debug_log.txt
}

; ------------------ CHECK IF WINDOW IS IN WINDOWED FULLSCREEN OR WINDOWED MODE ------------------ 
isWindowedFullScreen(winID) {
	;checks if the specified window is full screen
	If ( !winID )
		Return false

	WinGet style, Style, ahk_id %WinID%
	WinGetPos ,,,winW,winH, %winTitle%
	; 0x800000 is WS_BORDER.
	; 0x20000000 is WS_MINIMIZE.
	; no border and not minimized
	Return ((style & 0x20800000) or winH < A_ScreenHeight or winW < A_ScreenWidth) ? false : true
}

; ------------------ EXIT ------------------ 
Exit:
	Gdip_DeleteBrush(pBrush)
	SelectObject(hdc, obm)
	DeleteObject(hbm)
	DeleteDC(hdc)
	Gdip_DeleteGraphics(G)
	Gdip_Shutdown(pToken)
	ExitApp
Return