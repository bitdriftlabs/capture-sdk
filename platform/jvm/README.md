### Run local changes into external sample app via maven local

1. Under `/capture-sdk/platform/jvm/capture/build.gradle.kts` add version as shown below, so that you can easily identify it later:

**WARNING:** Do not commit these version changes to main

```
group = "io.bitdrift"

version = "-LOCAL-SNAPSHOT" // --> Add this line
```

2. Navigate to `capture-sdk/platform/jvm` and run `./gradlew clean publishToMavenLocal`. This will create the dependencies under ~/.m2/repository/io/bitdrift/capture/-LOCAL-SNAPSHOT

3. Go to your integration project build.gradle and include `mavenLocal()`:

```
allprojects {
    repositories {
        mavenLocal() // ---> Add this one

        jcenter()
        maven {
            url 'https://dl.bitdrift.io/sdk/android-maven'
            content {
                includeGroup 'io.bitdrift'
            }
        }
        google()
    }
}
```

4. Now on your app `build.gradle` use the generated version (e.g. `implementation 'io.bitdrift:capture:-LOCAL-SNAPSHOT'`)
