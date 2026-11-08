import Foundation
import UIKit

/// Generates a shareable PDF in the sandbox from one SAVED revision.
/// Only the user chooses if/where the report leaves the device.
enum CircuitProjectPDF {
    static func prepare(record:[String:Any],
                        client:String="",site:String="",reviewer:String="") throws -> URL {
        let content=try CircuitProjectReportText.build(record,
                                  client:client,site:site,reviewer:reviewer)
        let page=CGRect(x:0,y:0,width:612,height:792) // US Letter at 72 points
        let margin:CGFloat=43
        let right=page.width-margin
        let width=right-margin
        let top:CGFloat=57
        let bottom=page.height-48
        let regular=UIFont.systemFont(ofSize:10.0)
        let strong=UIFont.boldSystemFont(ofSize:11.4)
        let renderer=UIGraphicsPDFRenderer(bounds:page)
        let pdf=renderer.pdfData { ctx in
            var y:CGFloat=0
            var pageNo=0
            func beginPage(){
                ctx.beginPage()
                pageNo+=1
                y=top
                let label="ELECTRICAL WORKBENCH  |  "+(record["project"] as? String ?? "Project")+
                    "  |  Page \(pageNo)"
                (label as NSString).draw(at:CGPoint(x:margin,y:28),
                    withAttributes:[.font:strong,.foregroundColor:UIColor(red:0.07,green:0.3,blue:0.4,alpha:1)])
                UIColor(red:0.3,green:0.5,blue:0.6,alpha:1).setStroke()
                let line=UIBezierPath()
                line.move(to:CGPoint(x:margin,y:45))
                line.addLine(to:CGPoint(x:right,y:45));line.stroke()
            }
            beginPage()
            for raw in content.components(separatedBy:"\n") {
                let isHeading=raw.count<90 && raw.rangeOfCharacter(from:.uppercaseLetters) != nil &&
                    raw == raw.uppercased() && !raw.contains(": ") && !raw.isEmpty
                let font=isHeading ? strong : regular
                let attrs:[NSAttributedString.Key:Any]=[
                    .font:font,
                    .foregroundColor:isHeading ?
                        UIColor(red:0.06,green:0.3,blue:0.42,alpha:1):
                        UIColor(red:0.16,green:0.21,blue:0.29,alpha:1)
                ]
                let ns=raw as NSString
                let estimated=ns.boundingRect(
                    with:CGSize(width:width,height:10000),
                    options:[.usesLineFragmentOrigin,.usesFontLeading],
                    attributes:attrs,context:nil)
                let height=max(isHeading ? 17 : 13,ceil(estimated.height)+2)
                if y+height>bottom {beginPage()}
                ns.draw(in:CGRect(x:margin,y:y,width:width,height:height),
                    withAttributes:attrs)
                y+=height+(isHeading ? 4 : 0)
            }
        }
        let project=String(describing:record["project"] ?? "project")
            .replacingOccurrences(of:"[^A-Za-z0-9._-]",with:"-",
                                  options:.regularExpression)
        let title=String(describing:record["title"] ?? "circuit")
            .replacingOccurrences(of:"[^A-Za-z0-9._-]",with:"-",
                                  options:.regularExpression)
        let revision=String(describing:record["revision"] ?? "1")
        let filename=String(project.prefix(40))+"-"+String(title.prefix(40))+
            "-rev"+revision+"-"+String(UUID().uuidString.prefix(8))+".pdf"
        let url=FileManager.default.temporaryDirectory.appendingPathComponent(filename)
        try pdf.write(to:url,options:.atomic)
        return url
    }
}
