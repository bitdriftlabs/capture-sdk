#import <Foundation/Foundation.h>
#import <Capture/Capture.h>

#import "CAPViewController.h"

NSString * const kCaptureAPIKey= @"<YOUR API KEY GOES HERE>";
NSString * const kCaptureURLString = @"https://api.bitdrift.io";
NSString * const kDeviceId = @"ios-objc-helloworld";

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

@interface CAPViewController ()

@property (nonatomic, strong) NSArray<NSNumber *> *logLevels;
@property (nonatomic, assign) LogLevel selectedLogLevel;
@property (nonatomic, weak) UIButton *pickLogLevelButton;

@end

@implementation CAPViewController

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
    UIView *sendLogView = [self createSendLogView];
    UIView *copySessionIDView = [self createCopySessionIDView];

    UIStackView *view = [[UIStackView alloc]
                         initWithArrangedSubviews:@[[UIView new], sendLogView, copySessionIDView]];
    view.axis = UILayoutConstraintAxisVertical;
    view.distribution = UIStackViewDistributionEqualCentering;
    view.backgroundColor = [UIColor whiteColor];

    self.view = view;
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
    [CAPLogger
     configureWithAPIKey:kCaptureAPIKey
     sessionStrategy:[CAPSessionStrategy fixed]
     apiURL:[NSURL URLWithString:kCaptureURLString]
    ];

    [CAPLogger logInfo:@"An objective-c example app is launching" fields:nil];
}

- (void)refresh {
    [self.pickLogLevelButton
     setTitle:logLevelToString(self.selectedLogLevel)
     forState:UIControlStateNormal
    ];
}

// MARK: - Views

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

- (void)copySessionIDRow {
    UIPasteboard.generalPasteboard.string = [[CAPLogger class] sessionID];
}

@end
