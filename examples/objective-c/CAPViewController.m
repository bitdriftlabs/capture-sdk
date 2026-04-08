#import <Foundation/Foundation.h>
#import <Capture/Capture.h>

#import "CAPViewController.h"

static NSString * const kDefaultCaptureAPIKey = @"<YOUR API KEY GOES HERE>";
static NSString * const kDefaultCaptureURLString = @"https://api.bitdrift.io";
static NSString * const kSavedCaptureAPIKeyKey = @"capture_api_key";
static NSString * const kSavedCaptureURLKey = @"capture_api_url";

@interface CAPIssueLogger : NSObject <IssueReportCallback>
@end

@implementation CAPIssueLogger

- (void)onBeforeReportSendWithReport:(CAPIssueReport *)report {
    NSMutableDictionary<NSString *, NSString *> *fields = [NSMutableDictionary dictionaryWithDictionary:report.fields ?: @{}];
    fields[@"reportType"] = report.reportType ?: @"";
    fields[@"reason"] = report.reason ?: @"";
    fields[@"details"] = report.details ?: @"";
    fields[@"sessionID"] = report.sessionID ?: @"";
    [CAPLogger logInfo:@"Bitdrift IssueCallback" fields:fields];
}

@end

NSString *logLevelToString(LogLevel level) {
    switch (level) {
        case LogLevelTrace:
            return @"Trace";
        case LogLevelDebug:
            return @"Debug";
        case LogLevelInfo:
            return @"Info";
        case LogLevelWarning:
            return @"Warning";
        case LogLevelError:
            return @"Error";
    }

    return @"Unknown";
}

@interface CAPViewController () <UITextFieldDelegate>

@property (nonatomic, strong) NSArray<NSNumber *> *logLevels;
@property (nonatomic, assign) LogLevel selectedLogLevel;
@property (nonatomic, weak) UIButton *pickLogLevelButton;
@property (nonatomic, weak) UITextField *apiKeyTextField;
@property (nonatomic, weak) UITextField *apiURLTextField;
@property (nonatomic, strong) CAPIssueLogger *issueLogger;

@end

@implementation CAPViewController

- (NSString *)savedAPIKey {
    NSString *apiKey = [[NSUserDefaults standardUserDefaults] stringForKey:kSavedCaptureAPIKeyKey];
    return apiKey.length > 0 ? apiKey : kDefaultCaptureAPIKey;
}

- (NSString *)savedAPIURLString {
    NSString *apiURLString = [[NSUserDefaults standardUserDefaults] stringForKey:kSavedCaptureURLKey];
    return apiURLString.length > 0 ? apiURLString : kDefaultCaptureURLString;
}

- (instancetype)init {
    self = [super init];

    if (self) {
        self.logLevels = @[
            @(LogLevelTrace),
            @(LogLevelDebug),
            @(LogLevelInfo),
            @(LogLevelWarning),
            @(LogLevelError),
        ];
        self.selectedLogLevel = LogLevelInfo;
        [self setUpLogger];
    }

    return self;
}

- (void)loadView {
    UIView *rootView = [UIView new];
    rootView.backgroundColor = [UIColor whiteColor];

    UIView *configurationView = [self createConfigurationView];
    UIView *sendLogView = [self createSendLogView];
    UIView *copySessionURLView = [self createCopySessionURLView];
    UIView *crashView = [self createCrashView];
    UIView *copySessionIDView = [self createCopySessionIDView];

    UIStackView *contentStack = [[UIStackView alloc]
                                 initWithArrangedSubviews:@[configurationView, sendLogView, crashView, copySessionURLView, copySessionIDView]];
    contentStack.axis = UILayoutConstraintAxisVertical;
    contentStack.spacing = 16;
    contentStack.translatesAutoresizingMaskIntoConstraints = NO;

    UIScrollView *scrollView = [UIScrollView new];
    scrollView.alwaysBounceVertical = YES;
    scrollView.keyboardDismissMode = UIScrollViewKeyboardDismissModeInteractive;
    scrollView.translatesAutoresizingMaskIntoConstraints = NO;

    [scrollView addSubview:contentStack];
    [rootView addSubview:scrollView];

    UILayoutGuide *safeArea = rootView.safeAreaLayoutGuide;
    [NSLayoutConstraint activateConstraints:@[
        [scrollView.topAnchor constraintEqualToAnchor:safeArea.topAnchor],
        [scrollView.leadingAnchor constraintEqualToAnchor:safeArea.leadingAnchor],
        [scrollView.trailingAnchor constraintEqualToAnchor:safeArea.trailingAnchor],
        [scrollView.bottomAnchor constraintEqualToAnchor:safeArea.bottomAnchor],

        [contentStack.topAnchor constraintEqualToAnchor:scrollView.contentLayoutGuide.topAnchor constant:16],
        [contentStack.leadingAnchor constraintEqualToAnchor:scrollView.contentLayoutGuide.leadingAnchor constant:16],
        [contentStack.trailingAnchor constraintEqualToAnchor:scrollView.contentLayoutGuide.trailingAnchor constant:-16],
        [contentStack.bottomAnchor constraintEqualToAnchor:scrollView.contentLayoutGuide.bottomAnchor constant:-16],
        [contentStack.widthAnchor constraintEqualToAnchor:scrollView.frameLayoutGuide.widthAnchor constant:-32],
    ]];

    UITapGestureRecognizer *dismissKeyboardGesture = [[UITapGestureRecognizer alloc] initWithTarget:rootView action:@selector(endEditing:)];
    dismissKeyboardGesture.cancelsTouchesInView = NO;
    [rootView addGestureRecognizer:dismissKeyboardGesture];

    self.view = rootView;
}

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];

    [self refresh];
}

- (void)setSelectedLogLevel:(LogLevel)selectedLogLevel {
    _selectedLogLevel = selectedLogLevel;
    [self refresh];
}

// MARK: - Setup

- (void)setUpLogger {
    NSURL *apiURL = [NSURL URLWithString:[self savedAPIURLString]];
    self.issueLogger = [CAPIssueLogger new];
    CAPIssueCallbackConfiguration *issueCallbackConfiguration = [[CAPIssueCallbackConfiguration alloc]
                                                                initWithCallbackQueue:dispatch_get_main_queue()
                                                                issueReportCallback:self.issueLogger];
    CAPConfiguration *config = [[CAPConfiguration alloc] initWithEnableFatalIssueReporting:true
                                                               enableURLSessionIntegration:true
                                                                                 sleepMode:false
                                                                                    apiURL:apiURL
                                                                                rootFileURL:nil
                                                                 issueCallbackConfiguration:issueCallbackConfiguration];
    [CAPLogger
     startWithAPIKey:[self savedAPIKey]
     sessionStrategy:[CAPSessionStrategy activityBased]
      configuration: config
    ];

    [CAPLogger logInfo:@"An objective-c example app is launching" fields:nil];

    NSDictionary *previousRunInfo = [CAPLogger previousRunInfo];
    if (previousRunInfo != nil) {
        NSMutableDictionary<NSString *, NSString *> *fields = [NSMutableDictionary new];
        NSNumber *hasFatallyTerminated = previousRunInfo[@"hasFatallyTerminated"];
        fields[@"hasFatallyTerminated"] = hasFatallyTerminated.boolValue ? @"true" : @"false";
        [CAPLogger logInfo:@"Bitdrift PreviousRunInfo" fields:fields];
    }
}

- (void)refresh {
    [self.pickLogLevelButton
     setTitle:logLevelToString(self.selectedLogLevel)
     forState:UIControlStateNormal
    ];

    self.apiKeyTextField.text = [self savedAPIKey];
    self.apiURLTextField.text = [self savedAPIURLString];
}

// MARK: - Views

- (UIView *)createConfigurationView {
    UILabel *apiKeyLabel = [UILabel new];
    apiKeyLabel.text = @"API Key";

    UITextField *apiKeyTextField = [UITextField new];
    apiKeyTextField.borderStyle = UITextBorderStyleRoundedRect;
    apiKeyTextField.placeholder = kDefaultCaptureAPIKey;
    apiKeyTextField.autocorrectionType = UITextAutocorrectionTypeNo;
    apiKeyTextField.autocapitalizationType = UITextAutocapitalizationTypeNone;
    apiKeyTextField.returnKeyType = UIReturnKeyDone;
    apiKeyTextField.delegate = self;
    apiKeyTextField.text = [self savedAPIKey];

    UILabel *apiURLLabel = [UILabel new];
    apiURLLabel.text = @"API URL";

    UITextField *apiURLTextField = [UITextField new];
    apiURLTextField.borderStyle = UITextBorderStyleRoundedRect;
    apiURLTextField.placeholder = kDefaultCaptureURLString;
    apiURLTextField.autocorrectionType = UITextAutocorrectionTypeNo;
    apiURLTextField.autocapitalizationType = UITextAutocapitalizationTypeNone;
    apiURLTextField.keyboardType = UIKeyboardTypeURL;
    apiURLTextField.returnKeyType = UIReturnKeyDone;
    apiURLTextField.delegate = self;
    apiURLTextField.text = [self savedAPIURLString];

    UIButton *saveButton = [UIButton buttonWithType:UIButtonTypeSystem];
    saveButton.backgroundColor = UIColor.systemBlueColor;
    saveButton.tintColor = UIColor.whiteColor;
    [saveButton setTitle:@"Save Config" forState:UIControlStateNormal];
    [saveButton addTarget:self action:@selector(saveConfiguration) forControlEvents:UIControlEventTouchUpInside];

    UIStackView *configurationView = [[UIStackView alloc]
                                      initWithArrangedSubviews:@[apiKeyLabel, apiKeyTextField, apiURLLabel, apiURLTextField, saveButton]];
    configurationView.axis = UILayoutConstraintAxisVertical;
    configurationView.spacing = 8;

    self.apiKeyTextField = apiKeyTextField;
    self.apiURLTextField = apiURLTextField;

    return configurationView;
}

- (UIView *)createSendLogView {
    UIButton *sendLogButton = [UIButton buttonWithType:UIButtonTypeSystem];
    sendLogButton.backgroundColor = UIColor.lightGrayColor;
    [sendLogButton setTitle:@"Send Log"
                   forState:UIControlStateNormal];
    [sendLogButton addTarget:self
                      action:@selector(sendLog)
            forControlEvents:UIControlEventTouchUpInside];

    UIButton *pickLogLevelButton = [UIButton buttonWithType:UIButtonTypeDetailDisclosure];
    pickLogLevelButton.backgroundColor = UIColor.lightGrayColor;
    [pickLogLevelButton setTitle:logLevelToString(self.selectedLogLevel)
                        forState:UIControlStateNormal];
    [pickLogLevelButton addTarget:self
                           action:@selector(pickLogLevel)
                 forControlEvents:UIControlEventTouchUpInside];

    UIStackView *sendLogRow = [[UIStackView alloc]
                               initWithArrangedSubviews:@[sendLogButton, pickLogLevelButton]];
    [sendLogRow setCustomSpacing:5 afterView:sendLogButton];

    sendLogRow.layer.cornerRadius = 10;
    sendLogRow.axis = UILayoutConstraintAxisHorizontal;
    sendLogRow.distribution = UIStackViewDistributionFillEqually;
    sendLogRow.clipsToBounds = true;

    self.pickLogLevelButton = pickLogLevelButton;

    return sendLogRow;
}

- (UIView *)createCrashView {
    UIButton *crashButton = [UIButton buttonWithType:UIButtonTypeSystem];
    crashButton.backgroundColor = UIColor.systemRedColor;
    crashButton.tintColor = UIColor.whiteColor;
    [crashButton setTitle:@"Trigger Controlled Crash"
                 forState:UIControlStateNormal];
    [crashButton addTarget:self
                    action:@selector(triggerControlledCrash)
          forControlEvents:UIControlEventTouchUpInside];

    return crashButton;
}

- (UIView *)createCopySessionURLView {
    UIButton *copySessionURLButton = [UIButton buttonWithType:UIButtonTypeSystem];
    copySessionURLButton.backgroundColor = UIColor.systemGreenColor;
    copySessionURLButton.tintColor = UIColor.whiteColor;
    [copySessionURLButton setTitle:@"Copy Session URL"
                          forState:UIControlStateNormal];
    [copySessionURLButton addTarget:self
                             action:@selector(copySessionURL)
                   forControlEvents:UIControlEventTouchUpInside];

    return copySessionURLButton;
}

- (UIView *)createCopySessionIDView {
    UIStackView *copySessionIDRow = [UIStackView new];
    copySessionIDRow.axis = UILayoutConstraintAxisVertical;

    UIButton *copySessionIDButton = [UIButton buttonWithType:UIButtonTypeSystem];
    copySessionIDButton.backgroundColor = UIColor.systemBlueColor;
    copySessionIDButton.tintColor = UIColor.whiteColor;
    [copySessionIDButton setTitle:@"Copy Session ID"
                         forState:UIControlStateNormal];
    [copySessionIDButton addTarget:self
                            action:@selector(copySessionIDRow)
                  forControlEvents:UIControlEventTouchUpInside];

    UILabel *sessionIDLabel = [UILabel new];
    sessionIDLabel.text = [[CAPLogger class] sessionID];
    sessionIDLabel.adjustsFontSizeToFitWidth = true;

    [copySessionIDRow addArrangedSubview:copySessionIDButton];
    [copySessionIDRow addArrangedSubview:sessionIDLabel];

    return copySessionIDRow;
}

// MARK: - Actions

- (void)sendLog {
    switch (self.selectedLogLevel) {
        case LogLevelTrace:
            [CAPLogger logTrace:@"Trace Message" fields:@{@"test_field": @"test_field_value"}];
            break;
        case LogLevelDebug:
            [CAPLogger logDebug:@"Debug Message" fields:@{@"test_field": @"test_field_value"}];
            break;
        case LogLevelInfo:
            [CAPLogger logInfo:@"Info Message" fields:@{@"test_field": @"test_field_value"}];
            break;
        case LogLevelWarning:
            [CAPLogger logWarning:@"Warning Message" fields:@{ @"test_field": @"test_field_value" }];
            break;
        case LogLevelError:
            [CAPLogger logError:@"Error message" fields:@{@"test_field": @"test_field_value"}];
            break;
    }
}

- (void)pickLogLevel {
    UIAlertController *alert = [UIAlertController
                                alertControllerWithTitle:@"Log Level Selection"
                                message:@"Select Log Level"
                                preferredStyle:UIAlertControllerStyleActionSheet];

    for (NSNumber *logLevel in self.logLevels) {
        __weak typeof(self) weakSelf = self;
        UIAlertAction *action = [UIAlertAction
                                 actionWithTitle:logLevelToString([logLevel intValue])
                                 style:UIAlertActionStyleDefault
                                 handler:^(UIAlertAction * _Nonnull action) {
            weakSelf.selectedLogLevel = logLevel.intValue;
            [weakSelf refresh];
        }];
        [alert addAction:action];
    }

    [self presentViewController:alert animated:YES completion:nil];
}

- (void)saveConfiguration {
    [self.view endEditing:YES];

    NSString *apiKey = self.apiKeyTextField.text.length > 0 ? self.apiKeyTextField.text : kDefaultCaptureAPIKey;
    NSString *apiURLString = self.apiURLTextField.text.length > 0 ? self.apiURLTextField.text : kDefaultCaptureURLString;

    [[NSUserDefaults standardUserDefaults] setObject:apiKey forKey:kSavedCaptureAPIKeyKey];
    [[NSUserDefaults standardUserDefaults] setObject:apiURLString forKey:kSavedCaptureURLKey];
    [[NSUserDefaults standardUserDefaults] synchronize];

     UIAlertController *alert = [UIAlertController alertControllerWithTitle:@"Saved"
                                                                    message:@"Restart the app to apply the new settings."
                                                             preferredStyle:UIAlertControllerStyleAlert];
    [alert addAction:[UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleDefault handler:nil]];
    [self presentViewController:alert animated:YES completion:nil];
}

- (void)triggerControlledCrash {
    [CAPLogger logInfo:@"About to trigger controlled crash" fields:nil];
    NSArray<NSNumber *> *items = @[@1, @2, @3];
    NSLog(@"About to access out-of-bounds item: %@", items[3]);
}

- (void)copySessionURL {
    NSString *sessionURL = [[CAPLogger class] sessionURL];
    if (sessionURL.length > 0) {
        UIPasteboard.generalPasteboard.string = sessionURL;
    }
}

- (void)copySessionIDRow {
    UIPasteboard.generalPasteboard.string = [[CAPLogger class] sessionID];
}

- (BOOL)textFieldShouldReturn:(UITextField *)textField {
    [textField resignFirstResponder];
    return YES;
}

@end
