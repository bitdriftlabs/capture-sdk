// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use super::URLNormalizer;
use pretty_assertions::assert_eq;

#[test]
fn test_example_paths() {
  let sanitizer = URLNormalizer::default();

  let inputs = vec![
    ("/v1/ping", "/v1/ping"),
    ("/v1/ping/1234", "/v1/ping/<id>"),
    ("/v1/ping/1234/", "/v1/ping/<id>/"),
    ("/v1/ping/1234/test", "/v1/ping/<id>/test"),
    ("/v1/ping/1234/test/1234", "/v1/ping/<id>/test/<id>"),
    (
      "/v1/ping/1164888884904123170_1714376944.862/test/1164888884904123170_1714376944.862",
      "/v1/ping/<id>/test/<id>",
    ),
    (
      "/v1/ping/1164888884904123170_1714376944.862/test/1164888884904123170_1714376944.862/",
      "/v1/ping/<id>/test/<id>/",
    ),
    (
      "/v1/ping/885fa9b2-97f1-435b-8fe3-a461d3235924/test/885fa9b2-97f1-435b-8fe3-a461d3235924/",
      "/v1/ping/<id>/test/<id>/",
    ),
    ("1231231231/test", "<id>/test"),
    ("/1231231231/test", "/<id>/test"),
  ];

  for input in inputs {
    assert_eq!(sanitizer.normalize(input.0), input.1);
  }
}

#[test]
fn test_example_mediapaths() {
  let sanitizer = URLNormalizer::default();

  let inputs = vec![
    (
      "/driver-raw-recordings/v0.2/2024-04-29/0700/1164888884904123170_1714376944.862",
      "/driver-raw-recordings/v0.2/<id>/<id>/<id>",
    ),
    (
      "/card_scan/assets/2171ce5e7ff57ef8cb1cf73258a84f9d",
      "/card_scan/assets/<id>",
    ),
    (
      "/foobar-app-img/production/profilePicture/_upload_/1673172924989377458/\
       885fa9b2-97f1-435b-8fe3-a461d3235924/20230126T081810.686292Z",
      "/foobar-app-img/production/profilePicture/_upload_/<id>/<id>/<id>",
    ),
    (
      "/card_scan/assets/character_detector_2171ce5e7ff57ef8cb1cf73258a84f9d.tflite",
      "/card_scan/assets/character_detector_<id>.tflite",
    ),
  ];

  for input in inputs {
    assert_eq!(sanitizer.normalize(input.0), input.1);
  }
}

#[test]
fn test_example_hotstar_paths() {
  let sanitizer = URLNormalizer::default();

  let inputs = vec![(
    "/videos/hotstarint/1260022017/1000901071/v3/1714942203802/c811af9e895e40605b055c0c2c4227f1/\
     video_h265_fhd_sdr_1714944496230/hvc1/4_jaIKgOF/seg-21.m4s",
    "/videos/hotstarint/<id>/<id>/v3/<id>/<id>/video_h265_fhd_sdr_<id>/hvc1/4_jaIKgOF/seg-21.m4s",
  )];

  for input in inputs {
    assert_eq!(sanitizer.normalize(input.0), input.1);
  }
}

#[test]
fn test_3rd_part_sdk_paths() {
  let sanitizer = URLNormalizer::default();

  let inputs = vec![(
    "/meval/eyJkZXZpY2UiOnsia2V5IjoiMTMzMUU3QUQtRDZGMC00OENELUFGRTMtQjQ2NzY4RDYzMTVGIn0sImtpbmQiOi\
    JtdWx0aSIsImxkX2FwcGxpY2F0aW9uIjp7ImVudkF0dHJpYnV0ZXNWZXJzaW9uIjoiMS4wIiwiaWQiOiJtaWxlaXEuaW9z\
    LnN0YWdpbmciLCJrZXkiOiJmSGQ4akFNUGo3VEJ5Z0I4OXFIYVRocjk2S0pZVXpETkZBd215Y181MVVNPSIsImxvY2FsZS\
    I6ImVuX1VTIn0sImxkX2RldmljZSI6eyJlbnZBdHRyaWJ1dGVzVmVyc2lvbiI6IjEuMCIsImtleSI6IjI1NzE1N0MxLTZE\
    MEQtNDZENS04MDc4LTM5NTdDQ0QwMDc3MyIsIm1hbnVmYWN0dXJlciI6IkFwcGxlIiwibW9kZWwiOiJpUGhvbmUiLCJvcy\
    I6eyJmYW1pbHkiOiJBcHBsZSIsIm5hbWUiOiJpT1MiLCJ2ZXJzaW9uIjoiMTcuNCJ9fSwidXNlciI6eyJlbWFpbCI6ImFq\
    czhAYi5jb20iLCJrZXkiOiJuaEpYaEs2c0VlNkF4OFlHeVVQN0pBIn19",
    "/meval/<id>",
  ),
  (
    "/spi/v2/platforms/ios/gmp/1:261297331790:ios:ccf1c923699196809fad1f/settings",
    "/spi/v2/platforms/ios/gmp/<id>/settings",
  ),
  (
    "/spi/v2/platforms/android/gmp/1:639321596024:android:2e8626b6d04d73c1aecd8d/settings",
    "/spi/v2/platforms/android/gmp/<id>/settings",
  )
  ];

  for input in inputs {
    assert_eq!(sanitizer.normalize(input.0), input.1);
  }
}
