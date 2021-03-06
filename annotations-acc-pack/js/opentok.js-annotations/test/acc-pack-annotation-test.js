
var expect = chai.expect;
var AcceleratorPack = AcceleratorPack;
var AnnotationAccPack = AnnotationAccPack;
var OTSolution = OTSolution;

var _optionsap;
var _accPack;
var _connection;
var _session;
var _annotation;
var _options;

if (!Function.prototype.bind) {
    Function.prototype.bind = function() {
        var fn = this,
            args = Array.prototype.slice.call(arguments),
            context = args.shift();
        return function() {
            fn.apply(context, args);
        };
    };
}

var _annotationConstructor = function(options){
    _annotation = new AnnotationAccPack(options);
};

describe('Annotation Acc Pack', function() {

   before(function() {

       _optionsap = {
           apiKey: '', // Replace with an OpenTok API key
           sessionId: '', // Replace with a generated Session ID
           token: ''
      };

       _accPack = new AcceleratorPack(_optionsap);

       _connection = {
         connectionId: "ConnectionIDTest",
       };

       _session = {
         id: '', // Replace with a generated Session ID
         connection: _connection,
         apiKey: '', // Replace with an OpenTok API key
         token: ''
      };

       _options = {
         session: _session,
         extensionID: "ExtensionIDTest",
         accPack: _accPack
       };
   });

   after(function(){
     _annotation = null;
     _accPack = null;
     AcceleratorPack = null;
     AnnotationAccPack = null;

   });

   describe('Test New Annotation instance', function() {

       it('Constructor should create a AnnotationAccPack instance', function() {
            _annotation = new AnnotationAccPack(_options);
            expect(_annotation).not.to.be.null;
       });

   });

    describe('Test Annotation new instance when argument options is not complete', function() {

        before(function() {
            _annotation = null;
        });

        it('Should throw an exception when session is missing', function() {
              var options = {
                 extensionID: "ExtensionIDTest"
              };
              //expect(_screenSharingConstructor.bind(_screenSharingConstructor, options)).to.throw('Screen Share Acc Pack requires an OpenTok session');
              expect(_annotationConstructor.bind(_annotationConstructor, options)).to.throw('OpenTok Annotation Accelerator Pack requires an OpenTok session');
              expect(_annotation).to.be.null;
        });

        it('Should not throw an exception when accpack is missing', function() {
              var options = {
                  session: _session,
                  extensionID: "ExtensionIDTest"
                };
              expect(_annotationConstructor.bind(_annotationConstructor, options)).not.to.throw('');
              expect(_annotation).not.to.be.null;
        });
    });

    describe('Test Annotation new instance when session is not complete', function(){

      before(function() {
          _annotation = null;
      });

      it('Should throw an exception when sessionId is null', function() {
           var session = {
             id: "",
             connection: "Test",
             apiKey: "Test",
             token: "Test"
           };
           var options = {
             session: session,
             extensionID: "ExtensionIDTest"
           };
           expect(_annotationConstructor.bind(_annotationConstructor, options)).to.throw('The sessionId field cannot be null in the log entry');
           expect(_annotation).to.be.null;
      });

      it('Should throw an exception when connectionId is missing', function() {
           var session = {
             id: "Test",
             connection: "Test",
             apiKey: "Test",
             token: "Test"
           };
           var options = {
             session: session,
             extensionID: "ExtensionIDTest"
           };
           expect(_annotationConstructor.bind(_annotationConstructor, options)).to.throw('The connectionId field cannot be null in the log entry');
           expect(_annotation).to.be.null;
      });

      it('Should not throw an exception when apiKey and/or token is/are missing', function() {
           var session = {
             id: "Test",
             connection: _connection,
             apiKey: "",
             token: ""
           };
           var options = {
             session: session,
             extensionID: "ExtensionIDTest"
           };
           _annotation = new AnnotationAccPack(_options);
           expect(_annotation).not.to.be.null;
       });
    });

    describe('Test start Annotation', function() {
      it('Should start annotation', function() {
           _annotation = new AnnotationAccPack(_options);
           _annotation.start(_session, _options);
      });
      it('Should throw an exception when session is null', function() {
            _annotation = new AnnotationAccPack(_options);
            expect(_annotation.start.bind(_annotation.start, null, _options)).to.throw('OpenTok Annotation Widget requires an OpenTok session');
      });
      it('Should not throw an exception when options is null', function() {
             _annotation = new AnnotationAccPack(_options);
             _annotation.start(_session, null);
      });
    });

    describe('Test end Annotation', function() {
      it('Should end annotation', function() {
           _annotation = new AnnotationAccPack(_options);
           _annotation.start(_session, _options);
           _annotation.end();
           _annotation.end();

      });
    });

    describe('Test linkCanvas Annotation', function() {
      it('Should start annotation', function() {
           _annotation = new AnnotationAccPack(_options);
           _annotation.linkCanvas("","", _options);
      });
      it('Should not throw an exception when apiKey and/or token is/are missing', function() {
            _annotation = new AnnotationAccPack(_options);
            _annotation.linkCanvas("","", _options);
      });
      it('Should not throw an exception when apiKey and/or token is/are missing', function() {
             _annotation = new AnnotationAccPack(_options);
             _annotation.start(_session, null);
      });
    });

    // describe('Test resize canvas Annotation', function() {
    //   it('Should resize canvas', function() {
    //        _annotation = new AnnotationAccPack(_options);
    //        _annotation.resizeCanvas();
    //   });
    // });

    describe('Test add Subscriber To External Window Annotation', function() {
      it('Should add Subscriber To External Window', function() {
           _annotation = new AnnotationAccPack(_options);
           _annotation.addSubscriberToExternalWindow("stream");
      });
      // it('Should not throw an exception', function() {
      //      _annotation = new AnnotationAccPack(_options);
      //      _annotation.addSubscriberToExternalWindow("stream");
      // });

    });

    describe('Test show/hide toolbar Annotation', function() {
      it('Should show toolbar', function() {
           _annotation = new AnnotationAccPack(_options);
           _annotation.showToolbar();
      });
      it('Should hide toolbar', function() {
           _annotation = new AnnotationAccPack(_options);
           _annotation.hideToolbar();
      });
    });

});
