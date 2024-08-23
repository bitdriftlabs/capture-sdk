#import "AppDelegate.h"
#import "CAPViewController.h"
#import <UIKit/UIKit.h>

@implementation AppDelegate

- (BOOL)application:(UIApplication *)application
    didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
  CAPViewController *controller = [CAPViewController new];
  self.window = [[UIWindow alloc] initWithFrame:[UIScreen mainScreen].bounds];
  [self.window setRootViewController:controller];
  [self.window makeKeyAndVisible];

  NSLog(@"Finished launching!");
  return YES;
}

@end
