//
//  ReportReader.m
//  CrashTester
//
//  Created by Karl Stenerud on 04.07.25.
//

#import "ReportReader.h"
#import "KSBONJSONDecoder.h"

@interface DecoderStackEntry: NSObject
@property(nonatomic,readonly) NSObject *value;
@property(nonatomic) NSMutableData *nextString;
@end

@implementation DecoderStackEntry
@dynamic value;

- (instancetype)init {
    if((self = [super init])) {
        _nextString = [NSMutableData new];
    }
    return self;
}

- (NSObject *)value {
    [NSException raise:NSInternalInconsistencyException format:@"You must override %@ in a subclass", NSStringFromSelector(_cmd)];
    return nil;
}

- (void)addValue:(NSObject *)value {
    [NSException raise:NSInternalInconsistencyException format:@"You must override %@ in a subclass", NSStringFromSelector(_cmd)];
}

- (void)addString:(NSString *)string {
    [self addValue:string];
}

- (void)addStringChunk:(const char*)chunk length:(size_t)length isLast:(BOOL)isLast {
    [self.nextString appendBytes:chunk length:length];
    if(isLast) {
        [self addString:[[NSString alloc] initWithData:self.nextString encoding:NSUTF8StringEncoding]];
        self.nextString = [NSMutableData new];
    }
}

@end

@interface DecoderObjectEntry: DecoderStackEntry

@property(nonatomic) NSString *key;
@property(nonatomic) NSMutableDictionary *dict;

@end

@implementation DecoderObjectEntry

- (instancetype)init {
    if((self = [super init])) {
        _dict = [NSMutableDictionary new];
    }
    return self;
}

- (NSObject *)value {
    return self.dict;
}

- (void)addString:(NSString *)string {
    if(self.key == nil) {
        self.key = string;
    } else {
        [self addValue:string];
    }
}

- (void)addValue:(NSObject *)value {
    self.dict[self.key] = value;
    self.key = nil;
}

@end

@interface DecoderArrayEntry: DecoderStackEntry

@property(nonatomic) NSMutableArray *array;

@end

@implementation DecoderArrayEntry

- (instancetype)init {
    if((self = [super init])) {
        _array = [NSMutableArray new];
    }
    return self;
}

- (NSObject *)value {
    return self.array;
}

- (void)addValue:(NSObject *)value {
    [self.array addObject:value];
}

@end


@interface DecoderContext: NSObject
@property(nonatomic) NSMutableArray<DecoderStackEntry *> *stack;
@property(nonatomic) DecoderStackEntry *current;
@end
@implementation DecoderContext
@dynamic current;

- (instancetype)init {
    if ((self = [super init])) {
        _stack = [NSMutableArray new];
        [_stack addObject:[DecoderArrayEntry new]];
    }
    return self;
}

- (DecoderStackEntry *)current {
    return self.stack[self.stack.count-1];
}

- (void)addValue:(NSObject *)value {
    [self.current addValue:value];
}

- (void)addString:(NSString *)string {
    [self.current addString:string];
}

- (void)addStringChunk:(const char*)chunk length:(size_t)length isLast:(BOOL)isLast {
    [self.current addStringChunk:chunk length:length isLast:isLast];
}

- (void)addDict {
    [self.stack addObject:[DecoderObjectEntry new]];
}

- (void)addArray {
    [self.stack addObject:[DecoderArrayEntry new]];
}

- (void)endContainer {
    DecoderStackEntry *entry = self.current;
    [self.stack removeLastObject];
    [self.current addValue:entry.value];
}

- (NSDictionary *)decoded {
    DecoderArrayEntry *entry = (DecoderArrayEntry *)self.stack[0];
    NSObject *value = entry.array[0];
    if (![value isKindOfClass:NSDictionary.class]) {
        @throw @"Expected a dictionary";
    }
    return (NSDictionary *)value;
}

@end


static DecoderContext* getDecoderContext(void* userData) {
    return (__bridge DecoderContext*)userData;
}

static ksbonjson_decodeStatus onBoolean(bool value, void* userData) {
    [getDecoderContext(userData) addValue:@(value)];
    return KSBONJSON_DECODE_OK;
}

static ksbonjson_decodeStatus onUnsignedInteger(uint64_t value, void* userData) {
    [getDecoderContext(userData) addValue:@(value)];
    return KSBONJSON_DECODE_OK;
}

static ksbonjson_decodeStatus onSignedInteger(int64_t value, void* userData) {
    [getDecoderContext(userData) addValue:@(value)];
    return KSBONJSON_DECODE_OK;
}

static ksbonjson_decodeStatus onFloat(double value, void* userData) {
    [getDecoderContext(userData) addValue:@(value)];
    return KSBONJSON_DECODE_OK;
}

static ksbonjson_decodeStatus onBigNumber(KSBigNumber value, void* userData) {
    // We won't generate big numbers
    return KSBONJSON_DECODE_COULD_NOT_PROCESS_DATA;
}

static ksbonjson_decodeStatus onNull(void* userData) {
    [getDecoderContext(userData) addValue:NSNull.null];
    return KSBONJSON_DECODE_OK;
}

static ksbonjson_decodeStatus onString(const char* KSBONJSON_RESTRICT value,
                                       size_t length,
                                       void* KSBONJSON_RESTRICT userData) {
    NSData *data = [NSData dataWithBytes:value length:length];
    NSString *string = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
    [getDecoderContext(userData) addString:string];
    return KSBONJSON_DECODE_OK;
}

static ksbonjson_decodeStatus onStringChunk(const char* KSBONJSON_RESTRICT value,
                                            size_t length,
                                            bool isLastChunk,
                                            void* KSBONJSON_RESTRICT userData) {
    [getDecoderContext(userData) addStringChunk:value length:length isLast:isLastChunk];
    return KSBONJSON_DECODE_OK;
}

static ksbonjson_decodeStatus onBeginObject(void* userData) {
    [getDecoderContext(userData) addDict];
    return KSBONJSON_DECODE_OK;
}

static ksbonjson_decodeStatus onBeginArray(void* userData) {
    [getDecoderContext(userData) addArray];
    return KSBONJSON_DECODE_OK;
}

static ksbonjson_decodeStatus onEndContainer(void* userData) {
    [getDecoderContext(userData) endContainer];
    return KSBONJSON_DECODE_OK;
}

static ksbonjson_decodeStatus onEndData(void* userData) {
    return KSBONJSON_DECODE_OK;
}

static KSBONJSONDecodeCallbacks getCallbacks(void) {
    return (KSBONJSONDecodeCallbacks) {
        .onBoolean = onBoolean,
        .onUnsignedInteger = onUnsignedInteger,
        .onSignedInteger = onSignedInteger,
        .onFloat = onFloat,
        .onBigNumber = onBigNumber,
        .onNull = onNull,
        .onString = onString,
        .onStringChunk = onStringChunk,
        .onBeginObject = onBeginObject,
        .onBeginArray = onBeginArray,
        .onEndContainer = onEndContainer,
        .onEndData = onEndData,
    };
}

NSMutableDictionary *bitdrift_readReport(NSString* path) {
    NSData *data = [NSData dataWithContentsOfFile:path];
    if(data == nil) {
        return nil;
    }
    KSBONJSONDecodeCallbacks callbacks = getCallbacks();
    size_t decodedOffset = 0;
    DecoderContext *context = [DecoderContext new];
    ksbonjson_decodeStatus status = ksbonjson_decode(data.bytes,
                                                     data.length,
                                                     &callbacks,
                                                     (__bridge void *)(context),
                                                     &decodedOffset);
    switch(status) {
        case KSBONJSON_DECODE_OK:
            return [context decoded];
        case KSBONJSON_DECODE_INCOMPLETE:
            // We likely crashed while writing the report. Keep whatever it managed to write.
            return [context decoded];
        default:
            NSLog(@"Failed to read crash report at [%@]: %s", path, ksbonjson_describeDecodeStatus(status));
            return nil;
    }
}
