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
    let required_deps = vec![
      "androidx.appcompat:appcompat",
      "androidx.core:core",
      "androidx.lifecycle:lifecycle-common",
      "androidx.lifecycle:lifecycle-process",
      "androidx.metrics:metrics-performance",
      "androidx.startup:startup-runtime",
      "com.google.code.gson:gson",
      "com.google.flatbuffers:flatbuffers-java",
      "com.google.guava:listenablefuture",
      "com.google.protobuf:protobuf-kotlin-lite",
      "com.squareup.okhttp3:okhttp",
      "org.jetbrains.kotlin:kotlin-stdlib",
    ];

    let root = simple_xml::from_file(runfiles_path("capture_aar_pom_xml.xml")).unwrap();

    let deps = &root["dependencies"][0]["dependency"];

    let required_deps = into_parts(&required_deps);
    let present_deps: HashSet<(String, String)> = deps
      .iter()
      .map(|dep| {
        let group = dep["groupId"][0].content.clone();
        let artifact = dep["artifactId"][0].content.clone();
        (group, artifact)
      })
      .collect();

    for required_dep in &required_deps {
      assert!(
        present_deps.contains(required_dep),
        "missing required dependency {0}:{1}",
        required_dep.0,
        required_dep.1
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
