/** Zemismart
 *  Tuya Window Shade (v.0.1.0) Hubitat v0
 *  Tuya Window Shade (v.1.1.0) Hubitat v1 Gassgs
 *  Tuya Window Shade (v.1.2.0) Hubitat v2 Improvements Gassgs
 *  Tuya Window Shade (v.1.3.0) Hubitat v3 Blind Drive AM43 zb version Gassgs
 *  Tuya Window Shade (v.1.4.0) Hubitat v4 Added default speed option Gassgs
 *	Copyright 2020 iquix
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 This DTH is coded based on iquix's tuya-window-shade DTH.
 https://github.com/iquix/Smartthings/blob/master/devicetypes/iquix/tuya-window-shade.src/tuya-window-shade.groovy


 */

import groovy.json.JsonOutput
import hubitat.zigbee.zcl.DataType
import hubitat.helper.HexUtils

def driverVer() { return "1.4" }

metadata {
	definition(name: "Zemismart Zigbee-Tuya Blind Drive", namespace: "ShinJjang/Gassgs", author: "ShinJjang-iquix", ocfDeviceType: "oic.d.blind", vid: "generic-shade") {
		capability "Actuator"
		capability "Window Shade"
		capability "Switch Level"
        capability "Change Level"
        capability "Switch"
        capability "Sensor"
        
        attribute "speed", "integer"
        
        command "setSpeed",[[name: "speed",type: "NUMBER",description:"Motor speed (5 to 100)"]]

        fingerprint  profileId:"0104",inClusters:"0000,0004,0005,EF00",outClusters:"0019,000A",manufacturer:"_TZE200_zah67ekd",model:"TS0601",deviceJoinName: "Zemismart Zigbee Blind Drive"
        
    }

	preferences {
        input name: "Direction", type: "enum", title: "Direction Set", defaultValue: "00", options:["01": "Reverse", "00": "Forward"], displayDuringSetup: true
        input name: "speedRestore",type: "bool", title: "Enable restoring default speed after each move",required: true, defaultValue: true
        input name: "defaultSpeed",type: "number", title: "Default Speed",required: true, defaultValue: 100
        input name: "logInfoEnable",type: "bool", title: "Enable info text logging",required: true, defaultValue: true
	    input name: "logEnable",type: "bool", title: "Enable debug logging", required: true, defaultValue: true
    }
}

private getCLUSTER_TUYA() { 0xEF00 }
private getSETDATA() { 0x00 }

// Parse incoming device messages to generate events
def parse(String description) {
	if (description?.startsWith('catchall:') || description?.startsWith('read attr -')) {
		Map descMap = zigbee.parseDescriptionAsMap(description)
        if(logEnable) log.debug "Pasred Map $descMap"
		if (descMap?.clusterInt==CLUSTER_TUYA) {
			if ( descMap?.command == "01" || descMap?.command == "02" ) {
				def dp = zigbee.convertHexToInt(descMap?.data[3]+descMap?.data[2])
                if(logEnable) log.debug "dp = " + dp
				switch (dp) {
					case 1025: // 0x04 0x01: Confirm opening/closing/stopping (triggered from Zigbee)
                    	def data = descMap.data[6]
                    	if (descMap.data[6] == "00") {
                        	if(logEnable) log.debug "parsed opening"
                            levelEventMoving(100)
                        }
                        else if (descMap.data[6] == "02") {
                        	if(logEnable) log.debug "parsed closing"
                            levelEventMoving(0)
                        }
                        else {
                            if (logEnable) log.debug "parsed else case $dp open/close/stop zigbee $data"
                        }
                    	break;

					case 1031: // 0x04 0x07: Confirm opening/closing/stopping (triggered from remote)
                    	def data = descMap.data[6]
                    	if (descMap.data[6] == "01") {
                        	if (logEnable) log.trace "remote closing"
                            levelEventMoving(0)
                        }
                        else if (descMap.data[6] == "00") {
                        	if (logEnable) log.trace "remote opening"
                            levelEventMoving(100)
                        }
                        else {
                            if (logEnable) log.debug "parsed else case $dp open/close/stop remote $data"
                        }
                    	break;

					case 514: // 0x02 0x02: Started moving to position (triggered from Zigbee)
                    	def pos = zigbee.convertHexToInt(descMap.data[9])
						if(logEnable) log.debug "moving to position :"+pos
                        levelEventMoving(pos)
                        break;

					case 515: // 0x03: Arrived at position
                    	def pos = zigbee.convertHexToInt(descMap.data[9])
                        if(logEnable) log.debug description
                    	if(logInfoEnable) log.info "$device.label arrived at position :"+pos
                    	levelEventArrived(pos)
                        break;
                    
                    case 617: // 0x69: Speed set
                    	def speed = zigbee.convertHexToInt(descMap.data[9])
                        if(logEnable) log.debug description
                    	if(logInfoEnable) log.info "$device.label speed set to :"+speed
                        sendEvent(name:"speed",value:"$speed")
                        break;

                    log.warn "UN-handled CLUSTER_TUYA case  $dp $descMap"
				}
			}

		}
        else {
            //log.warn "UN-Pasred Map $descMap"
        }
	}
}

private levelEventMoving(currentLevel) {
	def lastLevel = device.currentValue("level")
	if(logEnable) log.debug "levelEventMoving - currentLevel: ${currentLevel} lastLevel: ${lastLevel}"
	if (lastLevel == "undefined" || currentLevel == lastLevel) { //Ignore invalid reports
		log.debug "Ignore invalid reports"
	} else {
		if (lastLevel < currentLevel) {
			sendEvent([name:"windowShade", value: "opening"])
		} else if (lastLevel > currentLevel) {
			sendEvent([name:"windowShade", value: "closing"])
		}
    }
}

private levelEventArrived(level) {
	if (level <= 1) {
    	sendEvent(name: "windowShade", value: "closed")
        sendEvent(name: "switch", value: "off")
        sendEvent(name: "level", value:"0")
        sendEvent(name: "position", value: "0")
    } else if (level >= 99) {
    	sendEvent(name: "windowShade", value: "open")
        sendEvent(name: "switch", value: "on")
        sendEvent(name: "level", value: "100")
        sendEvent(name: "position", value: "100")
    } else if (level > 1 && level < 99) {
		sendEvent(name: "windowShade", value: "partially open")
        sendEvent(name: "switch", value: "on")
        sendEvent(name: "level", value: (level))
        sendEvent(name: "position", value: (level))
    } else {
    	sendEvent(name: "windowShade", value: "unknown")
        //return
    }
    if (speedRestore){
        runIn(1,setDefaultSpeed)
    }
}

def setDefaultSpeed(){
    setSpeed(defaultSpeed)
}

def close() {
	if(logEnable) log.debug "close()"
    if(logInfoEnable) log.info "$device.label close()"
	def currentLevel = device.currentValue("level")
    if (currentLevel == 0) {
    	sendEvent(name: "windowShade", value: "closed")
        return
    }
	setLevel(0)
}

def open() {
	if(logEnable) log.debug "open()"
    if(logInfoEnable) log.info "$device.label open()"
    def currentLevel = device.currentValue("level")
    if (currentLevel == 100) {
    	sendEvent(name: "windowShade", value: "open")
        return
    }
	setLevel(100)
}

def pause() {
	if(logEnable) log.debug "pause()"
    if(logInfoEnable) log.info "$device.label pause()"
	sendTuyaCommand("0104", "00", "0101")

}

def setLevel(data, rate = null) {
	if(logEnable) log.debug "setLevel("+data+")"
    if(logInfoEnable) log.info "$device.label setLevel("+data+")"
    def currentLevel = device.currentValue("level")
    if (currentLevel == data) {
    	sendEvent(name: "level", value: currentLevel)
        sendEvent(name: "position", value: currentLevel) //HE capability attribute
        return
    }
    sendTuyaCommand("0202", "00", "04000000"+HexUtils.integerToHexString(data.intValue(), 1))
}

def setPosition(position){
    if(logEnable) log.debug "setPos to $position"
    setLevel(position, null)
}

def setSpeed(speed) {
	if(logEnable) log.debug"setSpeed: speed=${speed}"
	if (speed < 5 || speed > 100) {
		if(logEnable) log.debug "Invalid speed ${speed}. Speed must be between 5 - 100"
        if(logInfoEnable) log.info "$device.label - Invalid speed ${speed}, Speed must be between 5 - 100"
    }else{
        sendTuyaCommand("6902", "00", "04000000"+HexUtils.integerToHexString(speed.intValue(), 1))
    }
}

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

def installed() {
    log.info "installed..."
    sendEvent(name: "supportedWindowShadeCommands", value: JsonOutput.toJson(["open", "close", "pause"]), displayed: false)
    log.warn "debug logging is: ${logEnable == true}"
    if (logEnable) runIn(3600, logsOff)
	state.DriverVersion=driverVer()
}

def updated() {
	def val = Direction
	DirectionSet(val)
    log.warn "debug logging is: ${logEnable == true}"
    if (logEnable) runIn(1800, logsOff)
    state.DriverVersion=driverVer()
}

def DirectionSet(Dval) {
	if(logEnable) log.debug "Direction set ${Dval} "
    sendTuyaCommand("05040001", Dval, "")
}

private sendTuyaCommand(dp, fn, data) {
	if(logEnable) log.trace "send tuya command ${dp},${fn},${data}"
	zigbee.command(CLUSTER_TUYA, SETDATA, "00" + zigbee.convertToHexString(rand(256), 2) + dp + fn + data)
}

private rand(n) {
	return (new Random().nextInt(n))
}
def on () {
    open()
}

def off () {
    close()
}

def stopPositionChange(){
    pause()
}

def startPositionChange(direction) {
    if (direction == "open") {
        open()
    } else {
       close()
    }
}

def stopLevelChange(){
    pause()
}

def startLevelChange(direction) {
    if (direction == "up") {
        open()
    } else {
       close()
    }
}
