var PLUGIN_NAME = "element-face-matching-sdk";
var ARTIFACT_MAPPINGS_FILE = "artifact-mappings.json";
var CLASS_MAPPINGS_FILE = "class-mappings.json";
var JAVA_SRC_PATH = "./platforms/android/app/src/main/java";
var BUILD_GRADLE_PATH = "./platforms/android/app/build.gradle";
var PROJECT_PROPERTIES_PATH = "./platforms/android/project.properties";
var MANIFEST_PATH = "./platforms/android/app/src/main/AndroidManifest.xml";

var deferral, fs, path, now, recursiveDir;

function log(message) {
    console.log(PLUGIN_NAME + ": " + message);
}

function onFatalException(ex){
    log("EXCEPTION: " + ex.toString());
    deferral.resolve(); // resolve instead of reject so build doesn't fail
}

function run() {
    try {
        fs = require('fs');
        path = require('path');
        recursiveDir = require("recursive-readdir");
        now = require("performance-now")
    } catch (e) {
        throw("Failed to load dependencies: " + e.toString());
    }

    var startTime = now();

    var artifactMappings = JSON.parse(fs.readFileSync(path.join(__dirname, '.', ARTIFACT_MAPPINGS_FILE)).toString()),
        buildGradle = fs.readFileSync(BUILD_GRADLE_PATH).toString(),
        projectProperties = fs.readFileSync(PROJECT_PROPERTIES_PATH).toString(),
        androidManifest = fs.readFileSync(MANIFEST_PATH).toString();

    // Replace artifacts in build.gradle, project.properties & AndroidManifest.xml
    for(var oldArtifactName in artifactMappings){
        var newArtifactName = artifactMappings[oldArtifactName],
            artifactRegExpStr = sanitiseForRegExp(oldArtifactName) + ':[0-9.+]+';
        buildGradle = buildGradle.replace(new RegExp(artifactRegExpStr, 'gm'), newArtifactName);
        projectProperties = projectProperties.replace(new RegExp(artifactRegExpStr, 'gm'), newArtifactName);
    }
    fs.writeFileSync(BUILD_GRADLE_PATH, buildGradle, 'utf8');
    fs.writeFileSync(PROJECT_PROPERTIES_PATH, projectProperties, 'utf8');

    var classMappings = JSON.parse(fs.readFileSync(path.join(__dirname, '.', CLASS_MAPPINGS_FILE)).toString());

    // Replace class/package names in AndroidManifest.xml
    for (var oldClassName in classMappings){
        androidManifest = androidManifest.replace(new RegExp(oldClassName, 'g'), classMappings[oldClassName]);
    }
    fs.writeFileSync(MANIFEST_PATH, androidManifest, 'utf8');

    // Replace class/package names in source code
    recursiveDir(JAVA_SRC_PATH, [function(file, stats){
        return !file.match(".java");
    }], attempt(function(err, files){
        if(err) throw err;

        for(var filePath of files){
            var fileContents = fs.readFileSync(filePath).toString();
            for (var oldClassName in classMappings){
                fileContents = fileContents.replace(new RegExp(oldClassName, 'g'), classMappings[oldClassName]);
            }
            fs.writeFileSync(filePath, fileContents, 'utf8');
        }
        log("Processed " + files.length + " Java source files in " + parseInt(now() - startTime) + "ms");
        deferral.resolve();
    }));
}

function sanitiseForRegExp(str) {
    return str.replace(/[\-\[\]\/\{\}\(\)\*\+\?\.\\\^\$\|]/g, "\\$&");
}

function attempt(fn) {
    return function () {
        try {
            fn.apply(this, arguments);
        } catch (e) {
            onFatalException(e);
        }
    }
}

module.exports = function (ctx) {
    try{
        deferral = require('q').defer();
    }catch(e){
        e.message = 'Unable to load node module dependency \'q\': '+e.message;
        onFatalException(e);
        throw e;
    }
    attempt(run)();
    return deferral.promise;
};
