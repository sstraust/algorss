def parse_header_image(basic_info, page_data, existing_article):
    if page_data and page_data["soup"]:
        img_tag = page_data["soup"].find('meta', property='og:image')
        if img_tag:
            try:
                content = img_tag.get("content")
                if content:
                    return {"og_image_tag": str(content)}
            except Exception as e:
                pass
    return {}
        
