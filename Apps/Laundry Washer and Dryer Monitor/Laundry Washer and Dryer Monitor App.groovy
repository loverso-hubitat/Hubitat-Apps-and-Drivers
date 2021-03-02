/**
 *  ****************  Laundry Washer and Dryer Monitor App ****************
 *
 * Virtual Washing Machine and Dryer
 * Monitor the status of the devices in Hubitat and Google Home.
 * Send notifications when cycles complete/ notifications turn off when
 * washer or dryer doors open or when turned off in google home..
 *
 *
 *  Copyright 2021 Gassgs / Gary Gassmann
 *
 *
 *-------------------------------------------------------------------------------------------------------------------
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *-------------------------------------------------------------------------------------------------------------------
 *
 *
 *-------------------------------------------------------------------------------------------------------------------
 *
 *  Last Update: 1/24/2021
 *
 *  Changes:
 *
 *  V1.0.0 -        1-20-2021       First run
 *  V1.1.0 -        1-23-2021       Rewrite
 *  V1.2.0 -        1-24-2021       rewrite ,again
 *  V1.3.0 -        1-25-2021       Complete logic redo improvements
 *  V1.4.0 -        2-11-2021       Improved door/lid event handlers
 *  V1.5.0 -        2-18-2021       Redo -removed vibration sensors not reliable
 */

import groovy.transform.Field

definition(
    name: "Laundry Washer and Dryer Monitor ",
    namespace: "Gassgs",
    author: "Gary G",
    description: "Laundry Washer and Dryer Monitor App",
    category: "Utilities",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
)

preferences {

	section {

     paragraph(
         title: "Laundry Washer and Dryer Monitor",
         required: false,
    	"<div style='text-align:center'><b><big>: Laundry Washer and Dryer Monitor :</big></b></div>"
    	)
        paragraph( "<div style='text-align:center'><b>Washing Machine options</b></div>"
        )
        input(
            name:"washer",
            type:"capability.switch",
            title: "Virtual Laundry Machine device <b>-Washer-</b>",
            required: true
            )
    }
    section{
        input(
            name:"washerMeter",
            type:"capability.powerMeter",
            title: "Power reporting device conected to washer",
            multiple: false,
            submitOnChange: true
            )
    }
    section{
        input(
            name:"washerLidSwitch",
            type:"capability.switch",
            title:"Switch that monitors the washing machine lid",
            multiple: false,
            submitOnChange: true
            )
    }
    section{
        input(
            name:"washerOnThreshold",
            type:"number",
            title:"Power level that indicates the washer is running",
            required:true,
            submitOnChange: true
            )
        input(
            name:"washerOffThreshold",
            type:"number",
            title:"Power level that indicates the washer is idle",
            required:true,
            submitOnChange: true
            )
    }
    section{
        input(
            name:"washerTimeout",
            type:"number",
            title:"Minutes to wait after power level is below threshold confirming load is done",
            required:true,
            submitOnChange:true
            )
    }
    section{
        paragraph("<div style='text-align:center'><b>Washing complete notification options</b></div>"
        )
        input(
            name:"washerNotificationDevices",
            type:"capability.notification",
            title:"Push notification device",
            required: false,
            multiple: true
            )
    }
    section{
        input(
            name:"washerTtsDevices",
            type:"capability.speechSynthesis",
            title:"Choose TTS speaker(s)",
            required: false,
            multiple: true
            )
    }
    section{
        paragraph("<div style='text-align:center'><b>Dryer options</b></div>"
        )
        input(
            name:"dryer",
            type:"capability.switch",
            title:"Virtual Laundry Machine device <b>-Dryer-</b> ",
            required: true
            )
    }
    section{
        input(
            name:"dryerMeter",
            type:"capability.powerMeter",
            title: "Power reporting device conected to dryer",
            multiple: true,
            submitOnChange: true
            )
    }
    section{
        input(
            name:"dryerDoorSensor",
            type:"capability.contactSensor",
            title: "Contact sensor that monitors the dryer door",
            multiple: false,
            submitOnChange: true
            )
    }
    section{
        input(
            name:"dryerOnThreshold",
            type:"number",
            title:"Power level that indicates the dryer is running",
            required:true,
            submitOnChange: true
            )
        input(
            name:"dryerOffThreshold",
            type:"number",
            title:"Power level that indicates the dryer is idle",
            required:true,
            submitOnChange: true
            )
    }
    section{
        input(
            name:"dryerTimeout",
            type:"number",
            title:"Minutes to wait after power level is below threshold confirming load is dry",
            required:true,
            submitOnChange:true
            )
    }
    section{
        paragraph("<div style='text-align:center'><b>Dryer complete notification options</b></div>"
        )
        input(
            name:"dryerNotificationDevices",
            type:"capability.notification",
            title:"Push Notification device",
            required: false,
            multiple: true
            )
    }
    section{
        input(
            name:"dryerTtsDevices",
            type:"capability.speechSynthesis",
            title:"Choose TTS speaker(s)",
            required: false,
            multiple: true
            )
    }
    section{
        input(
            name:"logEnable",
            type:"bool",
            title: "Enable Info logging",
            required: true,
            defaultValue: false
            )
    }
}

def installed(){
    initialize()
}

def uninstalled(){
    logInfo ("uninstalling app")
}

def updated(){
    logInfo ("Updated with settings: ${settings}")
    unschedule()
    unsubscribe()
    initialize()
}

def initialize(){
    logInfo ("Settings: ${settings}")
    subscribe(settings.washer, "switch", washerSwitchHandler)
    subscribe(settings.dryer, "switch", dryerSwitchHandler)
    subscribe(settings.washerLidSwitch, "switch", washerLidHandler)
    subscribe(settings.dryerDoorSensor, "contact", dryerDoorHandler)
    subscribe(settings.washerMeter,"power", washerPowerHandler)
    subscribe(settings.dryerMeter,"power", dryerPowerHandler)
    subscribe(settings.washer, "status", washerStatusHandler)
    subscribe(settings.dryer, "status", dryerStatusHandler)
    logInfo ("subscribed to Events")
}

def washerSwitchHandler(evt){
    washerSwitch = evt.value
    logInfo ("Washer switch $washerSwitch")
    state.washerSwitchOn = (washerSwitch == "on")
    state.washerSwitchOff = (washerSwitch == "off")
    if (state.washerSwitchOff){
        unschedule(washerNotifications)
    }
}

def washerLidHandler(evt){
    washerLidStatus = evt.value
    logInfo ("Washer lid $washerLidStatus")
    state.washerLidClosed = (washerLidStatus == "on")
    state.washerLidOpen = (washerLidStatus == "off")
    if (state.washerLidOpen){
        settings.washer.off()
    }
}

def washerStatusHandler(evt){
    washerStatus = evt.value
    logInfo ("Washer $washerStatus")
    state.washerRunning = (washerStatus == "running")
    state.washerIdle = (washerStatus == "idle")
}

def washerPowerHandler(evt){
    meterValue = evt.value.toDouble()
    onThreshold = washerOnThreshold.toInteger()
    offThreshold = washerOffThreshold.toInteger()
    state.washerOn = (meterValue > onThreshold)
    state.washerOff = (meterValue < offThreshold)
    if  (state.washerOn&&state.washerIdle){
        logInfo ("Washer Power above threshold, setting to Running")
        settings.washer.update ("status","running")
        settings.washer.update("switch","on")
    }
    else if (state.washerOn&&state.washerRunning){
        logInfo ("Washer Power above threshold, already Running")
        unschedule(washerDone)
    }
    else if (state.washerOff&&state.washerRunning){
        logInfo ("Washer Power below threshold, checking if Done")
        runIn(washerTimeout * 60,washerDone)
    }
    else if (state.washerOff&&state.washerIdle){
        logInfo ("Washer Power below threshold, Washer already complete")
    }
}

def washerDone(){
    logInfo ("Washer load done, changing to idle")
    settings.washer.update ("status","idle")
    washerNotifications()
}

def washerNotifications(){
    logInfo ("Sending washer done message in 10 seconds")
    runIn(10,sendWasherMsg)
}

def sendWasherMsg(){
    if (state.washerSwitchOn&&state.washerIdle){
        logInfo ("sending washer done message")
        settings.washerNotificationDevices.deviceNotification("The washer is done, please move the wet laundry to the dryer to stop these messages")
        settings.washerTtsDevices.speak("The Washer is done please move the wet laundry to the dryer")
        runIn(15*60,washerNotifications)
    }
    else{
        logInfo ("Washer load complete, message will not be sent")
    }
}

def dryerSwitchHandler(evt){
    dryerSwitch = evt.value
    logInfo ("Dryer switch $dryerSwitch")
    state.dryerSwitchOn = (dryerSwitch == "on")
    state.dryerSwitchOff = (dryerSwitch == "off")
    if (state.dryerSwitchOff){
        unschedule(sendDryerMsg)
        unschedule(sendDryerMsg2)
    }
}

def dryerDoorHandler(evt){
    dryerDoorStatus = evt.value
    logInfo ("Dryer door $dryerDoorStatus")
    state.dryerDoorClosed = (dryerDoorStatus == "closed")
    state.dryerDoorOpen = (dryerDoorStatus == "open")
    if (state.dryerDoorOpen){
        settings.dryer.off()
    }
}

def dryerStatusHandler(evt){
    dryerStatus = evt.value
    logInfo ("Dryer $dryerStatus")
    state.dryerRunning = (dryerStatus == "running")
    state.dryerIdle = (dryerStatus == "idle")
}

def dryerPowerHandler(evt){
    meterValue = evt.value.toDouble()
    onThreshold = dryerOnThreshold.toInteger()
    offThreshold = dryerOffThreshold.toInteger()
    state.dryerOn = (meterValue > onThreshold)
    state.dryerOff = (meterValue < offThreshold)
    if  (state.dryerOn&&state.dryerIdle){
        logInfo ("Dryer Power above threshold setting to Running")
        settings.dryer.update ("status","running")
        settings.dryer.update("switch","on")
    }
    else if (state.dryerOn&&state.dryerRunning){
        logInfo ("Dryer Power above threshold, already Running")
        unschedule(dryerDone)
    }
    else if (state.dryerOn&&state.dryerSwitchOff){
        logInfo ("Dryer Power above threshold, dryer door must have opened,turning switch back on")
        unschedule(dryerDone)
        settings.dryer.update("switch","on")
    }
    else if (state.dryerOff&&state.dryerRunning){
        logInfo ("Dryer Power below threshold, checking if Done")
        runIn(dryerTimeout * 60,dryerDone)
    }
    else if (state.washerOff&&state.dryerIdle){
        logInfo ("Dryer Power below threshold, Dryer already complete")
    }
}

def dryerDone(){
    logInfo ("Dryer Done, changing to Idle")
    settings.dryer.update ("status","idle")
    dryerNotifications()      
}

def dryerNotifications(){
    logInfo ("Sending dryer done message in 10 seconds")
    runIn(10,sendDryerMsg)
}

def sendDryerMsg(){
    if (state.dryerSwitchOn&&state.dryerIdle){
        logInfo ("sending dryer notifications")
        settings.dryerNotificationDevices.deviceNotification("The laundry in the dryer is now dry")
        settings.dryerTtsDevices.speak("The laundry in the dryer is now dry")
        logInfo ("sending again in 30 minutes")
        runIn(30 *60,sendDryerMsg2)
    }
    else{
        logInfo ("Dryer load complete, message will not be sent")
    }
}

def sendDryerMsg2(){
    if (state.dryerSwitchOn&&state.dryerIdle){
        logInfo ("sending dryer notifications 2")
        settings.dryerNotificationDevices.deviceNotification("The laundry in the dryer can be put away now")
        settings.dryerTtsDevices.speak("The laundry in the dryer is dry and can be put away")
        settings.dryer.update("switch","off")
        logInfo ("dryer cycle complete")
    }
    else{
        logInfo ("Dryer load complete, 2nd message will not be sent")
    }
}

void logInfo(String Msg){
    if (settings?.logEnable !=false){
        log.info "$Msg"
    }
}
