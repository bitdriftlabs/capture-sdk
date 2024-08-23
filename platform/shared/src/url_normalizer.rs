// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./url_normalizer_sanitization_test.rs"]
mod url_normalizer_sanitization_test;

use regex::Regex;
use std::borrow::Cow;

pub(crate) struct URLNormalizer {
  regexes: Vec<Regex>,
}

impl URLNormalizer {
  #[must_use]
  pub(crate) fn normalize(&self, url_path: &str) -> String {
    let mut result = Cow::Borrowed(url_path);

    for regex in &self.regexes {
      result = Self::run_matcher(regex, result);
    }

    result.to_string()
  }

  fn run_matcher<'a>(regex: &Regex, input: Cow<'a, str>) -> Cow<'a, str> {
    let mut current = input;

    loop {
      let replace_result = regex.replace(&current, "${1}<id>${3}");
      // Protect ourselves from a case where there is a match but after the replacement the string
      // looks exactly the same as prior to it.
      if replace_result == current {
        return current;
      }

      match replace_result {
        Cow::Borrowed(_) => return current,
        Cow::Owned(new_url_path) => current = Cow::Owned(new_url_path),
      }
    }
  }
}

impl Default for URLNormalizer {
  fn default() -> Self {
    Self {
      regexes: vec![
        // Launchdarkly specific path.
        Regex::new("^(/meval/)([a-zA-Z0-9]+)()$").unwrap(),
        // Firebase specific path.
        Regex::new("^(/spi/v2/platforms/(?:.+)/gmp/)(.*)(/settings)$").unwrap(),
        // Looking for UUIDs (with or without `-`/`_`` separators). The UUIDs can be anywhere in a
        // string.
        Regex::new(
          "^(.*)([0-9a-f]{8}(?:-|_)?[0-9a-f]{4}(?:-|_)?[0-9a-f]{4}(?:-|_)?[0-9a-f]{4}(?:-|_)?\
           [0-9a-f]{12})(.*)$",
        )
        .unwrap(),
        // Date format, for example:
        // * 2024-10-10T123456.123456Z or 20241010T123456.123456Z.
        // * 2024-10-10 or 20241010.
        // The date:
        // * has to be at the beginning of the string or start immediately after `/` character.
        // * has to be at the end of the string or finish immediately before `/` character.
        Regex::new("^(.*/)*([0-9]{4}-?[0-9]{2}-?[0-9]{2}(?:T[0-9]{6}\\.[0-9]{6}Z)?)(/.*)*$")
          .unwrap(),
        // Looking for sequences of digits and `.`/`_` characters that are 5 or more characters
        // long. The digits sequence:
        // * has to be at the beginning of the string or start immediately after `/` character.
        // * has to be at the end of the string or finish immediately before `/` character.
        Regex::new("^(.*/)*([0-9_\\.]{5,})(/.*)*$").unwrap(),
        // Looking for sequences of digits that are 4 or more characters long.
        Regex::new("^(.*?)([0-9]{4,})(.*?)$").unwrap(),
      ],
    }
  }
}
