/*global cordova, module*/
var exec = require('cordova/exec');

var ElementSDK = {
    capture: function (id, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, "ElementFaceMatchingSDK", "capture", [id]);
    }
};

module.exports = ElementSDK;