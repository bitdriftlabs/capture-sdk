// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
mod test {

  use std::collections::HashSet;
  use std::path::PathBuf;

  fn runfiles_path(name: &str) -> PathBuf {
    let runfiles = std::env::var("RUNFILES_DIR").unwrap();

    PathBuf::from(runfiles).join("_main").join(name)
  }

  #[test]
  fn check_pom_file() {
    let permitted_exports = vec![
      "androidx.activity:activity-compose",
      "androidx.appcompat:appcompat",
      "androidx.compose.material:material",
      "androidx.compose.runtime:runtime",
      "androidx.compose.ui:ui-tooling",
      "androidx.compose.ui:ui",
      "androidx.core:core",
      "androidx.lifecycle:lifecycle-common",
      "androidx.lifecycle:lifecycle-process",
      "androidx.startup:startup-runtime",
      "com.google.code.gson:gson",
      "com.google.guava:listenablefuture",
      "com.google.flatbuffers:flatbuffers-java",
      "com.michael-bull.kotlin-result:kotlin-result-jvm",
      "com.squareup.okhttp3:okhttp",
      "org.jetbrains.kotlin:kotlin-stdlib",
      "com.google.protobuf:protobuf-kotlin-lite",
      "androidx.metrics:metrics-performance",
    ];

    let root = simple_xml::from_file(runfiles_path("capture_aar_pom_xml.xml")).unwrap();

    let deps = &root["dependencies"][0]["dependency"];

    let permitted_exports = into_parts(&permitted_exports);
    for dep in deps {
      let group = dep["groupId"][0].content.clone();
      let artifact = dep["artifactId"][0].content.clone();

      assert!(
        permitted_exports.contains(&(group.clone(), artifact.clone())),
        "unexpected .pom xml export {group}:{artifact}"
      );
    }
  }

  fn into_parts(coords: &[&str]) -> HashSet<(String, String)> {
    coords
      .iter()
      .map(|coord| {
        let mut parts = coord.split(':');
        (
          parts.next().unwrap().to_string(),
          parts.next().unwrap().to_string(),
        )
      })
      .collect()
  }
}
