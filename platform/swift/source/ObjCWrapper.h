#import <UIKit/UIKit.h>

@interface ObjCWrapper: NSObject

/**
 * Try to execute the block and catch any exceptions.
 */
+ (BOOL)doTry:(void(^)())block error:(NSError **)err;

@end
