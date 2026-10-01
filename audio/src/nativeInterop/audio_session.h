#import <AVFAudio/AVFAudio.h>
#import <UIKit/UIKit.h>

typedef NS_ENUM(NSInteger, ZootAudioSessionEvent) {
    ZootAudioSessionInactive,
    ZootAudioSessionResume,
    ZootAudioSessionLost,
    ZootAudioSessionReset,
    ZootAudioSessionForeground,
};

// Kotlin's platform bindings do not yet include the iOS 27 session notifications.
static inline NSArray<id> *ZootObserveAudioSession(void (^handler)(ZootAudioSessionEvent)) {
    NSNotificationCenter *center = NSNotificationCenter.defaultCenter;
    AVAudioSession *session = AVAudioSession.sharedInstance;
    NSMutableArray<id> *observers = [NSMutableArray array];
    void (^observe)(NSNotificationName, id, void (^)(NSNotification *)) =
        ^(NSNotificationName name, id object, void (^callback)(NSNotification *)) {
            [observers addObject:[center addObserverForName:name object:object queue:nil usingBlock:callback]];
        };

#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270000
    if (@available(iOS 27.0, *)) {
        observe(AVAudioSessionDidBecomeInactiveNotification, session, ^(NSNotification *notification) {
            AVAudioSessionDeactivationContext *context = notification.userInfo[AVAudioSessionDeactivationContextKey];
            if (context.source == AVAudioSessionDeactivationSourceSystem) {
                handler(ZootAudioSessionInactive);
            }
        });
        observe(AVAudioSessionResumptionRecommendationNotification, session, ^(NSNotification *notification) {
            AVAudioSessionResumptionContext *context = notification.userInfo[AVAudioSessionResumptionContextKey];
            if (context && context.recommendation == AVAudioSessionResumptionRecommendationShouldResume) {
                handler(ZootAudioSessionResume);
            }
        });
    } else
#endif
    {
        observe(AVAudioSessionInterruptionNotification, session, ^(NSNotification *notification) {
            NSNumber *type = notification.userInfo[AVAudioSessionInterruptionTypeKey];
            if (!type) return;
            if (type.unsignedIntegerValue == AVAudioSessionInterruptionTypeBegan) {
                handler(ZootAudioSessionInactive);
            } else {
                NSNumber *options = notification.userInfo[AVAudioSessionInterruptionOptionKey];
                if (options.unsignedIntegerValue & AVAudioSessionInterruptionOptionShouldResume) {
                    handler(ZootAudioSessionResume);
                }
            }
        });
    }
    observe(AVAudioSessionMediaServicesWereLostNotification, session, ^(NSNotification *notification) {
        handler(ZootAudioSessionLost);
    });
    observe(AVAudioSessionMediaServicesWereResetNotification, session, ^(NSNotification *notification) {
        handler(ZootAudioSessionReset);
    });
    observe(UIApplicationDidBecomeActiveNotification, nil, ^(NSNotification *notification) {
        handler(ZootAudioSessionForeground);
    });
    return observers;
}
