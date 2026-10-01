import XCTest

final class ZootSmokeTests: XCTestCase {
    @MainActor
    func testSettingsAndGameInput() {
        continueAfterFailure = false

        let app = XCUIApplication()
        app.launch()

        let settings = app.buttons["Settings"]
        XCTAssertTrue(settings.waitForExistence(timeout: 30))
        settings.tap()

        let overlay = app.switches["Performance overlay"]
        if !overlay.waitForExistence(timeout: 5) {
            XCTFail("Settings controls were not found: \(app.debugDescription)")
        }
        capture("Settings")
        app.buttons["Close settings"].tap()

        // The guest's title sequence is rendered in Metal, outside the accessibility tree.
        Thread.sleep(forTimeInterval: 15)
        capture("Title before Start")

        let start = app.buttons["Start · Pause"]
        XCTAssertTrue(start.waitForExistence(timeout: 5))
        start.tap()
        Thread.sleep(forTimeInterval: 2)
        capture("Game after Start")

        let action = app.buttons["A · Action"]
        XCTAssertTrue(action.waitForExistence(timeout: 5))
        action.tap()
        capture("Game after Start and A")

        XCUIDevice.shared.press(.home)
        XCTAssertTrue(app.wait(for: .runningBackground, timeout: 5))
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        XCTAssertTrue(springboard.icons["Zoot"].waitForExistence(timeout: 5))
        capture("Zoot launcher icon")
        app.activate()
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
        XCTAssertTrue(app.state == .runningForeground)

        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
        capture("Game after resume")
    }

    @MainActor
    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
