#import <Cocoa/Cocoa.h>
#import <objc/runtime.h>
#include <jni.h>

// Route the extended title area explicitly: Compose controls must not hit the
// native titlebar behind them. Use AppKit window actions for title gestures.
@interface CTTitlebarHitView : NSView
@property(nonatomic, weak) NSView *nativeBar;
@property(nonatomic) NSRect titleRectFromTop;
@end
@implementation CTTitlebarHitView
- (NSView *)hitTest:(NSPoint)point {
    if (self.hidden) return nil;
    NSPoint local = [self convertPoint:point fromView:self.superview];
    if (!NSPointInRect(local, self.bounds)) return nil;
    NSWindow *window = self.window;
    for (NSNumber *kind in @[@(NSWindowCloseButton), @(NSWindowMiniaturizeButton), @(NSWindowZoomButton)]) {
        NSButton *button = [window standardWindowButton:kind.integerValue];
        NSPoint inButton = [self convertPoint:local toView:button];
        if (!button.hidden && NSPointInRect(inButton, button.bounds)) return button;
    }
    NSPoint inWindow = [self convertPoint:local toView:nil];
    NSPoint fromTop = NSMakePoint(inWindow.x, window.frame.size.height - inWindow.y);
    // Keep the reserved traffic-light area draggable as well.
    if ((fromTop.x < 114 && fromTop.y < 64) || NSPointInRect(fromTop, self.titleRectFromTop)) return self;
    NSView *content = window.contentView;
    return [content hitTest:[self convertPoint:local toView:content.superview]];
}
- (BOOL)acceptsFirstMouse:(NSEvent *)event { return YES; }
- (BOOL)mouseDownCanMoveWindow { return NO; }
- (void)mouseDown:(NSEvent *)event {
    if (event.clickCount == 2) {
        NSString *action = [NSUserDefaults.standardUserDefaults stringForKey:@"AppleActionOnDoubleClick"];
        if ([action caseInsensitiveCompare:@"Minimize"] == NSOrderedSame && action) {
            [self.window performMiniaturize:nil];
        } else if (!action || [action caseInsensitiveCompare:@"None"] != NSOrderedSame) {
            [self.window performZoom:nil];
        }
    } else if (event.clickCount == 1) {
        [self.window performWindowDragWithEvent:event];
    }
}
@end

// Keep AppKit's actual buttons and their actions; change only their layout.
@interface CTWindowControls : NSObject
@property(nonatomic, weak) NSWindow *window;
@property(nonatomic) BOOL suspended;
@property(nonatomic) BOOL applying;
@property(nonatomic, strong) NSMutableArray *observers;
@property(nonatomic, strong) CTTitlebarHitView *titleHitView;
- (void)apply;
- (void)updateTitleHitFrame;
@end
@implementation CTWindowControls
- (void)apply {
    NSWindow *window = self.window;
    if (!window || self.suspended || self.applying || (window.styleMask & NSWindowStyleMaskFullScreen)) return;
    self.applying = YES;
    NSButton *close = [window standardWindowButton:NSWindowCloseButton];
    NSView *bar = close.superview;
    NSView *container = bar.superview;
    if (bar && container) {
        // Grow the native titlebar's hit-test area with its buttons, not just their drawing.
        NSRect frame = container.frame;
        CGFloat top = NSMaxY(frame);
        frame.size.height = 64;
        // Limit native hit testing to the space reserved for the traffic lights.
        frame.size.width = 114;
        frame.origin.y = top - frame.size.height;
        if (!NSEqualRects(container.frame, frame)) container.frame = frame;
        NSRect barFrame = bar.frame;
        barFrame.size.height = 64;
        barFrame.size.width = 114;
        barFrame.origin.y = 0;
        if (!NSEqualRects(bar.frame, barFrame)) bar.frame = barFrame;
        NSArray<NSButton *> *buttons = @[
            close, [window standardWindowButton:NSWindowMiniaturizeButton],
            [window standardWindowButton:NSWindowZoomButton]];
        CGFloat x = 34;
        for (NSButton *button in buttons) {
            NSRect rect = button.frame;
            // The floating toolbar starts 8 points below the top and is 58 points tall.
            const CGFloat centerFromTop = 8 + 58 / 2.0;
            rect.origin = NSMakePoint(x, bar.isFlipped ? centerFromTop - rect.size.height / 2 : 64 - centerFromTop - rect.size.height / 2);
            if (!NSEqualRects(button.frame, rect)) button.frame = rect;
            x += 20;
        }
    }
    [self updateTitleHitFrame];
    self.applying = NO;
}
- (void)updateTitleHitFrame {
    NSWindow *window = self.window;
    if (!window || self.suspended || (window.styleMask & NSWindowStyleMaskFullScreen)) return;
    NSView *frameView = window.contentView.superview;
    NSView *bar = [window standardWindowButton:NSWindowCloseButton].superview;
    if (frameView && bar) {
        if (!self.titleHitView) self.titleHitView = [[CTTitlebarHitView alloc] initWithFrame:NSZeroRect];
        self.titleHitView.nativeBar = bar;
        CGFloat height = MAX(64, NSMaxY(self.titleHitView.titleRectFromTop));
        NSRect bounds = frameView.bounds;
        NSRect frame = NSMakeRect(NSMinX(bounds), NSMaxY(bounds) - height, bounds.size.width, height);
        if (!NSEqualRects(self.titleHitView.frame, frame)) self.titleHitView.frame = frame;
        self.titleHitView.hidden = NO;
        if (self.titleHitView.superview != frameView)
            [frameView addSubview:self.titleHitView positioned:NSWindowAbove relativeTo:nil];
    }
}
- (void)dealloc {
    for (id observer in self.observers) [[NSNotificationCenter defaultCenter] removeObserver:observer];
}
@end
static char controlsKey;
static void onMain(dispatch_block_t block) {
    if ([NSThread isMainThread]) block(); else dispatch_sync(dispatch_get_main_queue(), block);
}
JNIEXPORT void JNICALL Java_io_github_lucf15_tiffrenderer_sample_desktop_MacWindowControls_install(JNIEnv *env, jobject self, jlong handle) {
    onMain(^{
        NSWindow *window = (__bridge NSWindow *)(void *)handle;
        if (!window || objc_getAssociatedObject(window, &controlsKey)) return;
        window.movableByWindowBackground = NO;
        CTWindowControls *controls = [CTWindowControls new];
        controls.window = window;
        controls.observers = [NSMutableArray new];
        objc_setAssociatedObject(window, &controlsKey, controls, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
        __weak CTWindowControls *weakControls = controls;
        NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
        for (NSNotificationName name in @[NSWindowDidResizeNotification, NSWindowDidBecomeKeyNotification,
                                        NSWindowDidExitFullScreenNotification]) {
            [controls.observers addObject:[center addObserverForName:name object:window queue:nil usingBlock:^(NSNotification *note) {
                CTWindowControls *current = weakControls;
                if ([note.name isEqual:NSWindowDidExitFullScreenNotification]) current.suspended = NO;
                [current apply];
                // AWT may finish its own titlebar layout after the notification.
                dispatch_async(dispatch_get_main_queue(), ^{ [weakControls apply]; });
            }]];
        }
        [controls.observers addObject:[center addObserverForName:NSWindowWillEnterFullScreenNotification object:window queue:nil usingBlock:^(NSNotification *note) {
            weakControls.suspended = YES;
            weakControls.titleHitView.hidden = YES;
        }]];
        NSView *bar = [window standardWindowButton:NSWindowCloseButton].superview;
        bar.postsFrameChangedNotifications = YES;
        [controls.observers addObject:[center addObserverForName:NSViewFrameDidChangeNotification object:bar queue:nil usingBlock:^(NSNotification *note) {
            [weakControls apply];
        }]];
        [controls apply];
    });
}
JNIEXPORT jdoubleArray JNICALL Java_io_github_lucf15_tiffrenderer_sample_desktop_MacWindowControls_positions(JNIEnv *env, jobject self, jlong handle) {
    __block struct { double values[6]; } resultData;
    onMain(^{
        NSWindow *window = (__bridge NSWindow *)(void *)handle;
        int i = 0;
        for (NSNumber *kind in @[@(NSWindowCloseButton), @(NSWindowMiniaturizeButton), @(NSWindowZoomButton)]) {
            NSButton *button = [window standardWindowButton:kind.integerValue];
            NSRect rect = [button convertRect:button.bounds toView:nil];
            resultData.values[i++] = NSMinX(rect);
            resultData.values[i++] = window.frame.size.height - NSMidY(rect);
        }
    });
    jdoubleArray result = (*env)->NewDoubleArray(env, 6);
    (*env)->SetDoubleArrayRegion(env, result, 0, 6, resultData.values);
    return result;
}

JNIEXPORT void JNICALL Java_io_github_lucf15_tiffrenderer_sample_desktop_MacWindowControls_activate(JNIEnv *env, jobject self, jlong handle) {
    onMain(^{
        NSWindow *window = (__bridge NSWindow *)(void *)handle;
        [NSApp activateIgnoringOtherApps:YES];
        [window makeKeyAndOrderFront:nil];
    });
}

JNIEXPORT void JNICALL Java_io_github_lucf15_tiffrenderer_sample_desktop_MacWindowControls_titleRegion(JNIEnv *env, jobject self, jlong handle,
    jdouble left, jdouble top, jdouble width, jdouble height) {
    // Layout can run while AppKit waits for AWT to finish a resize/fullscreen event.
    // Never synchronously wait for AppKit from that layout callback. Capturing the
    // window retains it until this ordered update has been handled on the main queue.
    NSWindow *window = (__bridge NSWindow *)(void *)handle;
    dispatch_block_t update = ^{
        CTWindowControls *controls = objc_getAssociatedObject(window, &controlsKey);
        if (!controls) return;
        controls.titleHitView.titleRectFromTop = NSMakeRect(left, top, MAX(0, width), MAX(0, height));
        [controls updateTitleHitFrame];
    };
    if ([NSThread isMainThread]) update(); else dispatch_async(dispatch_get_main_queue(), update);
}
