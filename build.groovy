

buildscript {
    dependencies {
         classpath 'org.jsoup:jsoup:1.11.3'
         classpath 'com.squareup.okhttp3:okhttp:3.11.0'
    }
}


wrapper {
    distributionType = Wrapper.DistributionType.ALL
    distributionSha256Sum = true
}
