#!/usr/bin/env ruby

# Builds, installs, and runs the Skeleton activity from the command line

require 'trollop'
require 'js_base'

PACKAGE = "com.js.cam"

class App

  def initialize
    @package = PACKAGE
    @verbose = nil
    @project_name = nil
    @project_directory = nil
    @project_test_directory = nil
  end

  def msg(message)
    return if !@verbose
    puts message
  end

  def clean
    msg("Cleaning...")
    scall("adb shell pm uninstall #{@package}")
    scall("adb shell pm uninstall #{@package}.test")
  end

  def runAndroidTests
    msg("Running Android tests...")
    output,_ = scall("adb shell am instrument -w -e class #{@package}/ApplicationTest #{@package}.test/android.test.InstrumentationTestRunner")
    msg(output)

    # We cannot rely on the return code; look for clues within the output instead

    if output.include?('FAILURES!!!') || output.include?('INSTRUMENTATION_CODE: 0')
      puts output if !@verbose
      die("Failed Android tests!!!")
    end
  end

  def listInstrumentation
    return if !@verbose
    output,_ = scall("adb shell pm list instrumentation")
    msg("Instrumentation:\n"+output)
  end

  def stopExistingApp
    msg("Stopping app...")
    scall("adb shell am force-stop #{@package}")
  end

  def install
    msg("Building and installing...")
    scall("gradle installDebug")
  end

  def installTests
    msg("Installing Android tests...")
    scall("gradle installDebugAndroidTest")
  end

  def runApp
    msg("Running...")
    scall("adb shell am start -n #{@package}/.MainActivity")
  end

  def runUnitTests
    msg("Running Java unit tests...")
    scall("gradle test")
  end

  def run(args = ARGV)
    options = Trollop::options(args) do
      opt :clean, "clean"
      opt :verbose, "verbose"
      opt :testonly, "test only"
      opt :omit_android, "omit Android tests", :short => 'a'
      opt :omit_java, "omit 'plain old' Java unit tests", :short => 'j'
      opt :runonly, "run only"
    end

    @verbose = options[:verbose]

    clean() if options[:clean]
    stopExistingApp()
    install()
    runUnitTests() if !options[:runonly] && !options[:omit_java]
    listInstrumentation() if options[:clean]
    if !options[:omit_android] && !options[:runonly]
      installTests()
      runAndroidTests()
    end
    runApp() if !options[:testonly]
  end

end

if __FILE__ == $0
  App.new.run()
end

