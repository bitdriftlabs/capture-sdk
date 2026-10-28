// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./feature_test.rs"]
mod tests;

use gherkin::{Feature, GherkinEnv, Step, StepType};
use serde::Serialize;
use std::path::{Path, PathBuf};

const FEATURE_DIRECTORY: &str = "features";

//
// FeaturePlan
//

/// A parsed feature whose steps are known to the Capture integration harness.
#[derive(Debug, Serialize)]
pub(crate) struct FeaturePlan {
  name: String,
  scenarios: Vec<ScenarioPlan>,
}

impl FeaturePlan {
  pub(crate) fn execute(&self, executor: &mut dyn FeatureExecutor) -> Result<(), String> {
    for scenario in &self.scenarios {
      executor.begin_scenario(&self.name, &scenario.name)?;
      for (index, action) in scenario.actions.iter().enumerate() {
        executor.execute_action(index + 1, scenario.actions.len(), action)?;
      }
    }
    Ok(())
  }
}

//
// FeatureExecutor
//

/// Executes platform operations selected by a feature's platform-specific adapter.
pub(crate) trait FeatureExecutor {
  fn begin_scenario(&mut self, feature: &str, scenario: &str) -> Result<(), String>;

  fn execute_action(
    &mut self,
    index: usize,
    total: usize,
    action: &FeatureAction,
  ) -> Result<(), String>;
}

//
// ScenarioPlan
//

#[derive(Debug, Serialize)]
struct ScenarioPlan {
  name: String,
  actions: Vec<FeatureAction>,
}

//
// FeatureAction
//

/// A platform-neutral operation expressed by a supported Gherkin step.
#[derive(Debug, Serialize)]
pub(crate) enum FeatureAction {
  InstallIosSample,
  SnapshotQueuedReports,
  RunMaestroFlow { flow: String },
  RelaunchIosSample,
  RunAssertion { assertion: String },
}

impl FeatureAction {
  pub(crate) fn description(&self) -> String {
    match self {
      Self::InstallIosSample => "install the iOS sample app".to_owned(),
      Self::SnapshotQueuedReports => "snapshot Capture's queued reports".to_owned(),
      Self::RunMaestroFlow { flow } => format!("run Maestro flow `{flow}`"),
      Self::RelaunchIosSample => "relaunch the iOS sample app".to_owned(),
      Self::RunAssertion { assertion } => format!("run assertion `{assertion}`"),
    }
  }
}

pub(crate) fn load_feature(input: &Path) -> Result<FeaturePlan, String> {
  let path = resolve_feature_path(input)?;
  let environment = GherkinEnv::new("en")
    .map_err(|error| format!("Could not initialize the Gherkin parser: {error}"))?;
  let feature = Feature::parse_path(&path, environment)
    .map_err(|error| format!("Could not parse feature {}: {error}", path.display()))?;
  plan_feature(&feature, &path)
}

fn resolve_feature_path(input: &Path) -> Result<PathBuf, String> {
  if input.is_file() {
    return Ok(input.to_path_buf());
  }
  if input.components().count() != 1 {
    return Err(format!("Feature {} does not exist.", input.display()));
  }

  let feature_directory = builtin_feature_directory();
  let mut matches = Vec::new();
  collect_features(&feature_directory, input, &mut matches)?;
  match matches.as_slice() {
    [] => Err(format!(
      "Could not find feature {} under {}. Pass a feature path or run `capture-dev test \
       <feature-name>` with an existing file name.",
      input.display(),
      feature_directory.display()
    )),
    [path] => Ok(path.clone()),
    _ => Err(format!(
      "Feature {} is ambiguous. Pass one of: {}",
      input.display(),
      matches
        .iter()
        .map(|path| path.display().to_string())
        .collect::<Vec<_>>()
        .join(", ")
    )),
  }
}

fn builtin_feature_directory() -> PathBuf {
  Path::new(env!("CARGO_MANIFEST_DIR")).join(FEATURE_DIRECTORY)
}

fn collect_features(
  directory: &Path,
  input: &Path,
  matches: &mut Vec<PathBuf>,
) -> Result<(), String> {
  let entries = std::fs::read_dir(directory).map_err(|error| {
    format!(
      "Could not inspect the built-in feature directory {}: {error}",
      directory.display()
    )
  })?;
  for entry in entries {
    let path = entry
      .map_err(|error| format!("Could not inspect a feature entry: {error}"))?
      .path();
    if path.is_dir() {
      collect_features(&path, input, matches)?;
    } else if path.file_name() == input.file_name() {
      matches.push(path);
    }
  }
  Ok(())
}

fn plan_feature(feature: &Feature, path: &Path) -> Result<FeaturePlan, String> {
  if feature.background.is_some() || !feature.rules.is_empty() {
    return Err(format!(
      "{} uses Gherkin Background or Rule, which capture-dev does not support yet.",
      path.display()
    ));
  }
  if feature.scenarios.is_empty() {
    return Err(format!("{} does not define a scenario.", path.display()));
  }

  let scenarios = feature
    .scenarios
    .iter()
    .map(|scenario| {
      if !scenario.examples.is_empty() {
        return Err(step_error(
          path,
          scenario.position.line,
          "Scenario Outline is not supported yet.",
        ));
      }
      let actions = scenario
        .steps
        .iter()
        .map(|step| action_for_step(path, step))
        .collect::<Result<Vec<_>, _>>()?;
      Ok(ScenarioPlan {
        name: scenario.name.clone(),
        actions,
      })
    })
    .collect::<Result<Vec<_>, _>>()?;

  Ok(FeaturePlan {
    name: feature.name.clone(),
    scenarios,
  })
}

fn action_for_step(path: &Path, step: &Step) -> Result<FeatureAction, String> {
  match (step.ty, step.value.as_str()) {
    (StepType::Given, "the iOS sample app is installed on a physical device") => {
      Ok(FeatureAction::InstallIosSample)
    },
    (StepType::Given, "I snapshot Capture's queued reports") => {
      Ok(FeatureAction::SnapshotQueuedReports)
    },
    (StepType::When, value) if value.starts_with("I run the Maestro flow ") => {
      quoted_value(path, step, "I run the Maestro flow ")
        .map(|flow| FeatureAction::RunMaestroFlow { flow })
    },
    (StepType::When, "I relaunch the iOS sample app") => Ok(FeatureAction::RelaunchIosSample),
    (StepType::Then, value) if value.starts_with("the assertion ") => {
      let assertion = quoted_value(path, step, "the assertion ")?;
      if !value.ends_with(" passes") {
        return Err(step_error(
          path,
          step.position.line,
          "Assertions must end with ` passes`.",
        ));
      }
      Ok(FeatureAction::RunAssertion { assertion })
    },
    _ => Err(step_error(
      path,
      step.position.line,
      &format!("Unsupported step: `{step}`."),
    )),
  }
}

fn quoted_value(path: &Path, step: &Step, prefix: &str) -> Result<String, String> {
  let value = step
    .value
    .strip_prefix(prefix)
    .and_then(|value| value.strip_suffix(" passes").or(Some(value)))
    .and_then(|value| value.strip_prefix('"'))
    .and_then(|value| value.strip_suffix('"'));
  value
    .filter(|value| !value.is_empty())
    .map(str::to_owned)
    .ok_or_else(|| {
      step_error(
        path,
        step.position.line,
        "Expected one non-empty double-quoted value.",
      )
    })
}

fn step_error(path: &Path, line: usize, message: &str) -> String {
  format!("{}:{line}: {message}", path.display())
}
