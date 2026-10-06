use anyhow::Error;
use std::backtrace::BacktraceStatus;
use std::io::Error as IoError;

#[test]
fn anyhow_preserves_error_api() {
  let error = Error::new(IoError::other("root"));
  assert_eq!(error.to_string(), "root");
  assert_eq!(error.root_cause().to_string(), "root");
  assert_eq!(error.chain().count(), 1);
  assert!(error.downcast_ref::<IoError>().is_some());
  let status = error.backtrace().status();
  if std::env::var_os("ANYHOW_EXPECT_DISABLED_BACKTRACE").is_some() {
    assert_eq!(status, BacktraceStatus::Disabled);
    assert_eq!(format!("{error:?}"), "root");
  }
  assert!(error.downcast::<IoError>().is_ok());
}
